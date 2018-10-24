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
package io.zeebe.exporter.record.value;

import io.zeebe.exporter.record.RecordValue;
import io.zeebe.protocol.intent.TimerIntent;

/**
 * Represents a timer event or command.
 *
 * <p>See {@link TimerIntent} for intents.
 */
public interface TimerRecordValue extends RecordValue {

  /** @return the key of the related activity instance. */
  long getActivityInstanceKey();

  /** @return the due date of the timer as Unix timestamp in millis. */
  long getDueDate();
}
