/*
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.camunda.zeebe.protocol.record.value;

import io.camunda.zeebe.protocol.record.ImmutableProtocol;
import io.camunda.zeebe.protocol.record.RecordValue;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.Intent;
import org.immutables.value.Value;

/**
 * Carries a command that arrived while its target was unable to process it immediately (e.g. a
 * suspended process instance), so it can be written back to the log later via {@code DRAIN}.
 */
@Value.Immutable
@ImmutableProtocol(builder = ImmutableBufferedCommandRecordValue.Builder.class)
public interface BufferedCommandRecordValue
    extends RecordValue, ProcessInstanceRelated, TenantOwned {

  /**
   * @return the record key of the buffered command at buffer time (i.e. {@code command.getKey()});
   *     what it identifies depends on the command — for example the job, timer, incident or
   *     user-task key it targets
   */
  long getCommandKey();

  /**
   * @return the value type of the buffered command
   */
  ValueType getValueType();

  /**
   * @return the intent of the buffered command
   */
  Intent getIntent();

  /**
   * @return the buffered command's record value
   */
  RecordValue getCommandValue();
}
