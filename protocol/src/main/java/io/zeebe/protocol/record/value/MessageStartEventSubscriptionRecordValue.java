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
package io.zeebe.protocol.record.value;

import io.zeebe.protocol.record.RecordValue;
import io.zeebe.protocol.record.intent.MessageStartEventSubscriptionIntent;

/**
 * Represents message start event subscription commands and events
 *
 * <p>See {@link MessageStartEventSubscriptionIntent} for intents.
 */
public interface MessageStartEventSubscriptionRecordValue extends RecordValue {

  /** @return the workflow key tied to the subscription */
  long getWorkflowKey();

  /** @return the id of the start event tied to the subscription */
  String getStartEventId();

  /** @return the name of the message */
  String getMessageName();
}
