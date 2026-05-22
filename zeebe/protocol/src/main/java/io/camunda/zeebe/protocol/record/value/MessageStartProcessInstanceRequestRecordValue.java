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
import java.util.Map;
import org.immutables.value.Value;

/**
 * Records of a cross-partition handshake by which {@code P_K = hash(correlationKey)} delegates the
 * creation of a message-start process instance to {@code P_B = hash(businessId)} — the partition
 * that owns the businessId uniqueness invariant for the targeted process definition.
 *
 * <p>The handshake carries every field needed by {@code P_B} to evaluate uniqueness, create the
 * process instance (the variables originally published with the message), and address the reply
 * back to the original start-event subscription on {@code P_K}. The source partition is encoded in
 * {@link #getMessageKey()} (Zeebe key partition bits) so no separate field is required; the target
 * partition {@code P_B} is derivable from {@link #getBusinessId()}.
 *
 * <p>See {@link io.camunda.zeebe.protocol.record.intent.MessageStartProcessInstanceRequestIntent}
 * for the intents that drive the request/reply flow.
 */
@Value.Immutable
@ImmutableProtocol(builder = ImmutableMessageStartProcessInstanceRequestRecordValue.Builder.class)
public interface MessageStartProcessInstanceRequestRecordValue extends RecordValue, TenantOwned {

  /**
   * @return the key of the buffered message on {@code P_K} that triggered this request; encodes the
   *     source partition in its high bits
   */
  long getMessageKey();

  /**
   * @return the name of the message that triggered the request
   */
  String getMessageName();

  /**
   * @return the correlation key of the originating publish; defines the source partition {@code
   *     P_K} via {@code hash(correlationKey)}
   */
  String getCorrelationKey();

  /**
   * @return the businessId carried by the originating publish; defines the target partition {@code
   *     P_B} via {@code hash(businessId)} and becomes the businessId of the created PI on success
   */
  String getBusinessId();

  /**
   * @return the key of the process definition to start; deployments are tenant-scoped, so this key
   *     uniquely identifies the definition without requiring tenantId in lookup keys
   */
  long getProcessDefinitionKey();

  /**
   * @return the BPMN process id of the targeted process definition; carried so {@code P_B} can
   *     address rejections and so observers can read the handshake without resolving keys
   */
  String getBpmnProcessId();

  /**
   * @return the BPMN id of the message start event that should be triggered on {@code P_B}
   */
  String getStartEventId();

  /**
   * @return the key of the message-start-event subscription on {@code P_K} that the reply must be
   *     applied to (the original subscription that buffered the message)
   */
  long getMessageStartEventSubscriptionKey();

  /**
   * @return the variables to seed the new process instance with — the variables of the originating
   *     published message; empty on reply records
   */
  Map<String, Object> getVariables();

  /**
   * @return the key of the process instance created on {@code P_B}; only set on success replies
   *     ({@link
   *     io.camunda.zeebe.protocol.record.intent.MessageStartProcessInstanceRequestIntent#START} and
   *     {@link
   *     io.camunda.zeebe.protocol.record.intent.MessageStartProcessInstanceRequestIntent#STARTED}),
   *     {@code -1} otherwise
   */
  long getProcessInstanceKey();
}
