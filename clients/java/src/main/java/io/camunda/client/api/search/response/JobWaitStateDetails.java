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

import io.camunda.client.api.search.enums.JobKind;
import io.camunda.client.api.search.enums.ListenerEventType;

/** Details of an element instance waiting on a job. */
public interface JobWaitStateDetails extends WaitStateDetails {

  String getJobKey();

  String getJobType();

  JobKind getJobKind();

  ListenerEventType getListenerEventType();

  Integer getRetries();

  /**
   * @return {@code true} if the job is parked until a secret it references is resolved rather than
   *     waiting for a worker. It is set once an activation attempt finds the secret not cached, and
   *     stays {@code true} after a failed resolution, which also raises a {@code
   *     SECRET_RESOLUTION_ERROR} incident on the job.
   */
  Boolean isWaitingForSecretResolution();
}
