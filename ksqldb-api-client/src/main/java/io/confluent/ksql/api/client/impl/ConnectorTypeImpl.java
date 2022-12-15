/*
 * Copyright 2021 Confluent Inc.
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

package io.confluent.ksql.api.client.impl;

import io.confluent.ksql.api.client.ConnectorType;
import java.util.Objects;

public class ConnectorTypeImpl implements ConnectorType {

  private final Type type;

  public ConnectorTypeImpl(final String type) {
    this(Type.valueOf(type.trim().toUpperCase()));
  }

  private ConnectorTypeImpl(final Type type) {
    this.type = type;
  }

  @Override
  public Type getType() {
    return type;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final ConnectorTypeImpl that = (ConnectorTypeImpl) o;
    return type == that.type;
  }

  @Override
  public int hashCode() {
    return Objects.hash(type);
  }

  @Override
  public String toString() {
    return "ConnectorType{"
        + "type=" + type
        + '}';
  }
}
