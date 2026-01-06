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
import java.util.List;
import org.immutables.value.Value;

@Value.Immutable
@ImmutableProtocol(builder = ImmutableConditionalSubscriptionRecordValue.Builder.class)
public interface ConditionalSubscriptionRecordValue
    extends RecordValue, ProcessInstanceRelated, TenantOwned {

  /**
   * The key of the scope in which the condition is evaluated. Scopes should be assigned for
   * different element types as follows:
   *
   * <p>Intermediate catch event → element itself
   *
   * <p>Boundary event → attached activity
   *
   * <p>Event subprocess start event → flow scope that is enclosing the event subprocess
   *
   * <p>Root level start event → nothing, just evaluate through endpoint call using process
   * definition key
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

  /**
   * The variable names that the condition evaluation depends on
   *
   * @return a list of variable names
   */
  List<String> getVariableNames();

  /**
   * The variable events that the condition evaluation depends on
   *
   * @return a list of variable events
   */
  List<String> getVariableEvents();

  /**
   * Indicates whether the condition is interrupting or non-interrupting.
   *
   * @return true if the condition is interrupting, false otherwise
   */
  boolean isInterrupting();
}
