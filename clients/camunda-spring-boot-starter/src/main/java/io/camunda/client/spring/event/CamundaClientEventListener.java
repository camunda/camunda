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
package io.camunda.client.spring.event;

import io.camunda.client.lifecycle.CamundaClientLifecycleAware;
import java.util.Set;
import org.springframework.context.event.EventListener;

public class CamundaClientEventListener {

  private final Set<CamundaClientLifecycleAware> camundaClientLifecycleAwareSet;

  public CamundaClientEventListener(
      final Set<CamundaClientLifecycleAware> camundaClientLifecycleAwareSet) {
    this.camundaClientLifecycleAwareSet = camundaClientLifecycleAwareSet;
  }

  @EventListener
  public void handleStart(final CamundaClientCreatedSpringEvent evt) {
    camundaClientLifecycleAwareSet.forEach(
        camundaClientLifecycleAware ->
            camundaClientLifecycleAware.onStart(evt.getClient(), evt.getClientName()));
  }

  @EventListener
  public void handleStop(final CamundaClientClosingSpringEvent evt) {
    camundaClientLifecycleAwareSet.forEach(
        camundaClientLifecycleAware ->
            camundaClientLifecycleAware.onStop(evt.getClient(), evt.getClientName()));
  }
}
