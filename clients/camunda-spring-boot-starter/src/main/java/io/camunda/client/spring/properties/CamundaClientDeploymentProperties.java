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

public class CamundaClientDeploymentProperties {

  /** Indicates if the `@Deployment` annotation is processed. */
  private boolean enabled = true;

  /**
   * Indicates if the resources selected by the deployment annotation have to reside in the same jar
   * as the annotated class.
   */
  private boolean ownJarOnly = false;

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(final boolean enabled) {
    this.enabled = enabled;
  }

  public boolean isOwnJarOnly() {
    return ownJarOnly;
  }

  public void setOwnJarOnly(final boolean ownJarOnly) {
    this.ownJarOnly = ownJarOnly;
  }

  @Override
  public String toString() {
    return "CamundaClientDeploymentProperties{"
        + "enabled="
        + enabled
        + ", ownJarOnly="
        + ownJarOnly
        + '}';
  }
}
