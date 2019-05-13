/*
 * Zeebe Workflow Engine
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
package io.zeebe.engine.processor;

import io.zeebe.logstreams.log.LoggedEvent;
import io.zeebe.msgpack.UnpackedObject;
import io.zeebe.protocol.impl.record.RecordMetadata;

@SuppressWarnings({"rawtypes"})
public class TypedEventImpl implements TypedRecord {
  protected LoggedEvent rawEvent;
  protected RecordMetadata metadata;
  protected UnpackedObject value;

  public void wrap(LoggedEvent rawEvent, RecordMetadata metadata, UnpackedObject value) {
    this.rawEvent = rawEvent;
    this.metadata = metadata;
    this.value = value;
  }

  public int getMaxValueLength() {
    return this.rawEvent.getMaxValueLength();
  }

  public long getPosition() {
    return rawEvent.getPosition();
  }

  public long getSourceEventPosition() {
    return rawEvent.getSourceEventPosition();
  }

  @Override
  public long getKey() {
    return rawEvent.getKey();
  }

  @Override
  public RecordMetadata getMetadata() {
    return metadata;
  }

  @Override
  public UnpackedObject getValue() {
    return value;
  }

  @Override
  public String toString() {
    return "TypedEventImpl{" + "metadata=" + metadata + ", value=" + value + '}';
  }
}
