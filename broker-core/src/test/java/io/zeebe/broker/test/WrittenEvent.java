/*
 * Zeebe Broker Core
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package io.zeebe.broker.test;

import io.zeebe.protocol.impl.RecordMetadata;

public class WrittenEvent<E> {
  final long key;
  final E value;
  final RecordMetadata metadata;

  public WrittenEvent(long key, E value, RecordMetadata metadata) {
    this.key = key;
    this.value = value;
    this.metadata = metadata;
  }

  public long getKey() {
    return key;
  }

  public E getValue() {
    return value;
  }

  public RecordMetadata getMetadata() {
    return metadata;
  }
}
