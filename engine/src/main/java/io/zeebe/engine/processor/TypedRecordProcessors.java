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

import io.zeebe.protocol.RecordType;
import io.zeebe.protocol.ValueType;
import io.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.zeebe.protocol.intent.Intent;
import java.util.ArrayList;
import java.util.List;

public final class TypedRecordProcessors {

  private final RecordProcessorMap recordProcessorMap = new RecordProcessorMap();
  private final List<StreamProcessorLifecycleAware> lifecycleListeners = new ArrayList<>();

  private TypedRecordProcessors() {}

  public static TypedRecordProcessors processors() {
    return new TypedRecordProcessors();
  }

  // TODO: could remove the ValueType argument as it follows from the intent
  public TypedRecordProcessors onEvent(
      ValueType valueType, Intent intent, TypedRecordProcessor<?> processor) {
    return onRecord(RecordType.EVENT, valueType, intent, processor);
  }

  private TypedRecordProcessors onRecord(
      RecordType recordType,
      ValueType valueType,
      Intent intent,
      TypedRecordProcessor<?> processor) {
    recordProcessorMap.put(recordType, valueType, intent.value(), processor);

    return this;
  }

  public TypedRecordProcessors onCommand(
      ValueType valueType, Intent intent, TypedRecordProcessor<?> processor) {
    return onRecord(RecordType.COMMAND, valueType, intent, processor);
  }

  public <T extends UnifiedRecordValue> TypedRecordProcessors onCommand(
      ValueType valueType, Intent intent, CommandProcessor<T> commandProcessor) {
    return onCommand(valueType, intent, new CommandProcessorImpl<>(commandProcessor));
  }

  public TypedRecordProcessors withListener(StreamProcessorLifecycleAware listener) {
    this.lifecycleListeners.add(listener);
    return this;
  }

  public RecordProcessorMap getRecordProcessorMap() {
    return recordProcessorMap;
  }

  public List<StreamProcessorLifecycleAware> getLifecycleListeners() {
    return lifecycleListeners;
  }
}
