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

import io.zeebe.engine.state.ZeebeState;
import io.zeebe.msgpack.UnpackedObject;
import io.zeebe.protocol.clientapi.RecordType;
import io.zeebe.protocol.clientapi.ValueType;
import io.zeebe.protocol.intent.Intent;
import java.util.ArrayList;
import java.util.List;

public class TypedEventStreamProcessorBuilder {
  protected final TypedStreamEnvironment environment;

  protected RecordProcessorMap eventProcessors = new RecordProcessorMap();
  protected List<StreamProcessorLifecycleAware> lifecycleListeners = new ArrayList<>();

  private ZeebeState zeebeState;

  public TypedEventStreamProcessorBuilder(TypedStreamEnvironment environment) {
    this.environment = environment;
  }

  // TODO: could remove the ValueType argument as it follows from the intent
  public TypedEventStreamProcessorBuilder onEvent(
      ValueType valueType, Intent intent, TypedRecordProcessor<?> processor) {
    return onRecord(RecordType.EVENT, valueType, intent, processor);
  }

  private TypedEventStreamProcessorBuilder onRecord(
      RecordType recordType,
      ValueType valueType,
      Intent intent,
      TypedRecordProcessor<?> processor) {
    eventProcessors.put(recordType, valueType, intent.value(), processor);

    return this;
  }

  public TypedEventStreamProcessorBuilder onCommand(
      ValueType valueType, Intent intent, TypedRecordProcessor<?> processor) {
    return onRecord(RecordType.COMMAND, valueType, intent, processor);
  }

  public <T extends UnpackedObject> TypedEventStreamProcessorBuilder onCommand(
      ValueType valueType, Intent intent, CommandProcessor<T> commandProcessor) {
    return onCommand(valueType, intent, new CommandProcessorImpl<>(commandProcessor));
  }

  public TypedEventStreamProcessorBuilder withListener(StreamProcessorLifecycleAware listener) {
    this.lifecycleListeners.add(listener);
    return this;
  }

  /** Only required if a stream processor writes events to its own stream. */
  public TypedEventStreamProcessorBuilder zeebeState(ZeebeState zeebeState) {
    this.zeebeState = zeebeState;
    return this;
  }

  public TypedStreamProcessor build() {

    return new TypedStreamProcessor(
        environment.getCommandResponseWriter(),
        eventProcessors,
        lifecycleListeners,
        environment.getEventRegistry(),
        zeebeState,
        environment);
  }
}
