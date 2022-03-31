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
import org.immutables.value.Value;

/** Error records are written on unexpected errors during the processing phase. */
@Value.Immutable
@ImmutableProtocol
public interface ErrorRecordValue extends RecordValue {

  /**
   * @return the exception message, which causes this error record.
   */
  String getExceptionMessage();

  /**
   * @return the stacktrace, which belongs to the exception
   */
  String getStacktrace();

  /**
   * @return the position of the event, which causes this error
   */
  long getErrorEventPosition();

  /**
   * @return the process instance key, which is related to the failed event. If the event is not
   *     process instance related, then this will return -1
   */
  long getProcessInstanceKey();
}
