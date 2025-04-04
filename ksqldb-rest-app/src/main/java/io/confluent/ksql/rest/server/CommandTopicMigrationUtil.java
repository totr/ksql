/*
 * Copyright 2022 Confluent Inc.
 *
 * Licensed under the Confluent Community License (the "License"); you may not use
 * this file except in compliance with the License.  You may obtain a copy of the
 * License at
 *
 * http://www.confluent.io/confluent-community-license
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OF ANY KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations under the License.
 */

package io.confluent.ksql.rest.server;

import io.confluent.ksql.rest.entity.CommandId;
import io.confluent.ksql.rest.server.computation.Command;
import io.confluent.ksql.rest.server.computation.CommandStore;
import io.confluent.ksql.rest.server.computation.InternalTopicSerdes;
import io.confluent.ksql.rest.server.computation.QueuedCommand;
import io.confluent.ksql.util.KsqlConfig;
import io.confluent.ksql.util.KsqlException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

// CHECKSTYLE_RULES.OFF: ClassDataAbstractionCoupling
public final class CommandTopicMigrationUtil {
  // CHECKSTYLE_RULES.ON: ClassDataAbstractionCoupling
  private static final Logger log = LogManager.getLogger(CommandTopicMigrationUtil.class);
  public static final CommandId MIGRATION_COMMAND_ID =
      new CommandId(CommandId.Type.CLUSTER, "migration", CommandId.Action.ALTER);

  private CommandTopicMigrationUtil() {

  }

  public static void commandTopicMigration(
      final String commandTopic,
      final KsqlRestConfig restConfig,
      final KsqlConfig config
  ) {
    final TopicPartition topicPartition = new TopicPartition(commandTopic, 0);

    // produce a higher version command to the old command topic so that other servers
    // stop writing to it since they'll be degraded
    final Producer<CommandId, Command> oldBrokerProducer = new KafkaProducer<>(
        config.originals(),
        InternalTopicSerdes.serializer(),
        InternalTopicSerdes.serializer()
    );
    final ProducerRecord<CommandId, Command> degradedCommand = new ProducerRecord<>(
        commandTopic,
        topicPartition.partition(),
        MIGRATION_COMMAND_ID,
        new Command(
            "",
            Collections.emptyMap(),
            Collections.emptyMap(),
            Optional.empty(),
            Optional.of(Integer.MAX_VALUE - 1),
            Integer.MAX_VALUE
        )
    );
    oldBrokerProducer.send(degradedCommand);
    oldBrokerProducer.close();

    // read all the commands from the old command topic
    final org.apache.kafka.clients.consumer.Consumer<byte[], byte[]> oldBrokerConsumer =
        new KafkaConsumer<>(
            config.originals(),
            new ByteArrayDeserializer(),
            new ByteArrayDeserializer()
        );
    oldBrokerConsumer.assign(Collections.singleton(topicPartition));

    final List<QueuedCommand> commands = CommandTopic.getAllCommandsInCommandTopic(
        oldBrokerConsumer,
        topicPartition,
        Optional.empty(),
        CommandStore.POLLING_TIMEOUT_FOR_COMMAND_TOPIC
    );
    oldBrokerConsumer.close();

    // remove the incompatible command that was just written to the command topic
    final List<QueuedCommand> commandsToMigrate = new ArrayList<>();
    for (QueuedCommand command : commands) {
      final CommandId currentCommandId = command.getAndDeserializeCommandId();
      if (currentCommandId.equals(MIGRATION_COMMAND_ID)) {
        log.info("skipping migration command sent to old command "
            + "topic when migrating to new one");
      } else {
        commandsToMigrate.add(command);
      }
    }

    // producer for the new command topic
    final Map<String, Object> newBrokerProducerConfigs = restConfig.getCommandProducerProperties();
    newBrokerProducerConfigs.put(
        ProducerConfig.TRANSACTIONAL_ID_CONFIG,
        config.getString(KsqlConfig.KSQL_SERVICE_ID_CONFIG) + "-migration-producer"
    );
    try (Producer<byte[], byte[]> newBrokerProducer = new KafkaProducer<>(
        newBrokerProducerConfigs,
        new ByteArraySerializer(),
        new ByteArraySerializer()
    )) {
      newBrokerProducer.initTransactions();
      newBrokerProducer.beginTransaction();

      // re-create command topic
      for (QueuedCommand command : commandsToMigrate) {
        final ProducerRecord<byte[], byte[]> producerRecord = new ProducerRecord<>(
            commandTopic,
            0,
            command.getCommandId(),
            command.getCommand());
        newBrokerProducer.send(producerRecord);
      }
      newBrokerProducer.commitTransaction();
    } catch (final Exception e) {
      throw new KsqlException("error producing messages to command topic during migration", e);
    }
    log.info("Finished migrating command topic for ksql with id {}",
        config.getString(KsqlConfig.KSQL_SERVICE_ID_CONFIG));
  }
}
