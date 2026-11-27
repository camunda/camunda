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
package io.camunda.client.spring.properties;

import io.camunda.client.api.BackpressureProfile;

/**
 * How the client reacts when the cluster rejects a request because it is temporarily overloaded.
 */
public class CamundaClientBackpressureProperties {

  /**
   * The profile that tunes how the client reacts when the cluster rejects a request because it is
   * temporarily overloaded: balanced, conservative, aggressive, legacy or disabled. Handling
   * backpressure is opt-in, so the default is disabled. With any other profile, a request that the
   * cluster rejects with backpressure (an HTTP 429, an HTTP 503 or 500 that reports resource
   * exhaustion, or a gRPC status of resource exhausted) is retried up to two more times with
   * exponential backoff and jitter, within the request timeout. Except for legacy, the client also
   * limits the number of concurrent requests while the cluster is under pressure. The profiles
   * correspond to those of the Camunda TypeScript SDK. Legacy retries but does not limit, and
   * disabled restores the behavior from before this handling existed.
   */
  private BackpressureProfile profile = BackpressureProfile.DISABLED;

  public BackpressureProfile getProfile() {
    return profile;
  }

  public void setProfile(final BackpressureProfile profile) {
    this.profile = profile;
  }

  @Override
  public String toString() {
    return "CamundaClientBackpressureProperties{" + "profile=" + profile + '}';
  }
}
