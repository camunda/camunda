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
package io.camunda.zeebe.client.api.response;

/**
 * @deprecated since 8.8 for removal in 8.9, replaced by {@link
 *     io.camunda.client.api.response.CorrelateMessageResponse}
 */
@Deprecated
public interface CorrelateMessageResponse {
  /**
   * Returns the record key of the message that was correlated.
   *
   * @return record key of the message.
   */
  Long getMessageKey();

  /**
   * Returns the tenant id of the message that was correlated.
   *
   * @return identifier of the tenant that owns the correlated message.
   */
  String getTenantId();

  /**
   * Returns the process instance key this messages was correlated with.
   *
   * @return key of the correlated process instance
   */
  Long getProcessInstanceKey();
}
