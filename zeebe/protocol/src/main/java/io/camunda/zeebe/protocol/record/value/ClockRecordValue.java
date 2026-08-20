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
import io.camunda.zeebe.protocol.record.intent.ClockIntent;
import org.immutables.value.Value;

/**
 * Represents a stream clock event or command.
 *
 * <p>See {@link ClockIntent} for intents.
 */
@Value.Immutable
@ImmutableProtocol(builder = ImmutableClockRecordValue.Builder.class)
public interface ClockRecordValue extends RecordValue {
  /**
   * Returns the value of this clock modification, if any.
   *
   * <ol>
   *   <li>If the associated intent is a pin, then the time is a Unix timestamp as epoch
   *       milliseconds
   *   <li>If the associated intent is an offset, then time would be the duration in milliseconds
   *   <li>If the associated intent is a reset, then this value has no meaning, and will be 0
   * </ol>
   */
  long getTime();
}
