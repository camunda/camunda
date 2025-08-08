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
import io.camunda.zeebe.protocol.record.RecordValueWithVariables;
import java.util.Map;
import org.immutables.value.Value;

@Value.Immutable
@ImmutableProtocol(builder = ImmutableCompensationSubscriptionRecordValue.Builder.class)
public interface CompensationSubscriptionRecordValue extends RecordValueWithVariables, TenantOwned {

  /**
   * @return the key of the process instance
   */
  long getProcessInstanceKey();

  /**
   * @return the key of the process definition
   */
  long getProcessDefinitionKey();

  /**
   * @return the element id of the activity with the compensation handler
   */
  String getCompensableActivityId();

  /**
   * @return the element id of compensation throw event
   */
  String getThrowEventId();

  /**
   * @return the element instance key of compensation throw event
   */
  long getThrowEventInstanceKey();

  /**
   * @return the element id of the compensation handler
   */
  String getCompensationHandlerId();

  /**
   * @return the instance key of the compensation handler
   */
  long getCompensationHandlerInstanceKey();

  /**
   * @return the instance key of the flow scope that contains the activity with the compensation
   *     handler
   */
  long getCompensableActivityScopeKey();

  /**
   * @return the instance key of the activity with the compensation handler
   */
  long getCompensableActivityInstanceKey();

  /**
   * @return the local variables of activity with compensation handler
   */
  @Override
  Map<String, Object> getVariables();
}
