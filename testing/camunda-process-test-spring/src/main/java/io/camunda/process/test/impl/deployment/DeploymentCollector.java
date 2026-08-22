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
package io.camunda.process.test.impl.deployment;

import io.camunda.client.api.response.DeploymentEvent;
import io.camunda.client.event.CamundaPostDeploymentEvent;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import org.springframework.context.event.EventListener;

/** Collects the deployment events from the Spring process application. */
public class DeploymentCollector {

  private final Collection<DeploymentEvent> deploymentEvents = new ArrayList<>();

  @EventListener
  public void onDeploymentCreated(final CamundaPostDeploymentEvent event) {
    deploymentEvents.addAll(event.getDeployments());
  }

  /**
   * @return an unmodifiable collection of deployment events
   */
  public Collection<DeploymentEvent> getDeploymentEvents() {
    return Collections.unmodifiableCollection(deploymentEvents);
  }

  /** Clears all collected deployment events. */
  public void clear() {
    deploymentEvents.clear();
  }
}
