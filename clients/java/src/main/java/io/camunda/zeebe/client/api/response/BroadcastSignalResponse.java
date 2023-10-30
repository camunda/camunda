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

public interface BroadcastSignalResponse {
  /**
   * Returns the unique key of the signal that was broadcasted.
   *
   * @return the unique key of the signal.
   */
  long getKey();

  /**
   * Returns the tenant id of the signal that was broadcasted.
   *
   * @return the identifier of the tenant that owns the broadcasted signal.
   */
  String getTenantId();
}
