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

package io.confluent.ksql.serde.protobuf;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThrows;

import com.google.common.collect.ImmutableMap;
import io.confluent.ksql.util.KsqlException;
import org.junit.Test;

public class ProtobufPropertiesTest {

  @Test
  public void shouldGetDefaultUnwrapPrimitives() {
    // Given:
    final ProtobufProperties properties = new ProtobufProperties(ImmutableMap.of());

    // When/Then:
    assertThat(properties.getUnwrapPrimitives(), is(false));
  }

  @Test
  public void shouldGetExplicitUnwrapPrimitives() {
    // Given:
    final ProtobufProperties properties = new ProtobufProperties(ImmutableMap.of(
        ProtobufProperties.UNWRAP_PRIMITIVES, ProtobufProperties.UNWRAP
    ));

    // When/Then:
    assertThat(properties.getUnwrapPrimitives(), is(true));
  }

  @Test
  public void shouldThrowWithUnsupportedProperty() {
    // When:
    final Exception e = assertThrows(KsqlException.class,
        () -> new ProtobufProperties(ImmutableMap.of("some_property", "value")));

    // Then:
    assertThat(e.getMessage(), is("PROTOBUF does not support the following configs: [some_property]"));
  }
}