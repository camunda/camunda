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
package io.camunda.client.api.search.response;

import java.time.OffsetDateTime;

public interface CorrelatedMessageSubscription {

  String getCorrelationKey();

  OffsetDateTime getCorrelationTime();

  String getElementId();

  Long getElementInstanceKey();

  Long getMessageKey();

  String getMessageName();

  Integer getPartitionId();

  String getProcessDefinitionId();

  Long getProcessDefinitionKey();

  Long getProcessInstanceKey();

  /**
   * Returns the key of the root process instance. The root process instance is the top-level
   * ancestor in the process instance hierarchy.
   *
   * <p><strong>Note:</strong> This field is {@code null} for process instance hierarchies created
   * before version 8.9.
   *
   * @return the root process instance key, or {@code null} for data created before version 8.9
   */
  Long getRootProcessInstanceKey();

  Long getSubscriptionKey();

  String getTenantId();

  /**
   * Returns the business id associated with this correlated message subscription.
   *
   * <p>For a message start event correlation, this is the business id carried by the correlating
   * message, which was stamped on the started process instance to enforce its uniqueness. For a
   * catch, boundary, or intermediate event correlation, this is the business id of the subscribing
   * process instance, captured when the subscription was opened.
   *
   * <p><strong>Note:</strong> This field is {@code null} when the relevant process instance has no
   * business id.
   *
   * @return the business id, or {@code null} if none applies
   */
  String getBusinessId();
}
