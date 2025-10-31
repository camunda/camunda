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

@Value.Immutable
@ImmutableProtocol(builder = ImmutableConditionSubscriptionRecordValue.Builder.class)
public interface ConditionSubscriptionRecordValue
    extends RecordValue, ProcessInstanceRelated, TenantOwned {

  /**
   * The key of the scope in which the condition is evaluated.
   *
   * @return the scope key
   */
  long getScopeKey();

  /**
   * @return the key of the related element instance.
   */
  long getElementInstanceKey();

  /**
   * @return the key of the related process instance
   */
  @Override
  long getProcessInstanceKey();

  /**
   * @return the process definition key
   */
  long getProcessDefinitionKey();

  /**
   * @return the id of the catch event tied to the subscription
   */
  String getCatchEventId();

  /**
   * The condition expression
   *
   * @return the condition expression as a String
   */
  String getCondition();
}
