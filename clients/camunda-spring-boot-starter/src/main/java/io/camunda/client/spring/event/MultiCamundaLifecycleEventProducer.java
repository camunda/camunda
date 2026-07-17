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

import io.camunda.client.spring.bean.CamundaClientRegistry;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.SmartLifecycle;

/**
 * Multi-client counterpart of {@link CamundaLifecycleEventProducer}: publishes one {@link
 * CamundaClientCreatedSpringEvent} / {@link CamundaClientClosingSpringEvent} per configured client
 * in the {@link CamundaClientRegistry}, each carrying the client's configured name. This lets
 * lifecycle-aware components (e.g. the job-worker registration) run once per client.
 *
 * <p>Wired by {@code CamundaAutoConfiguration} on the unified path (skipped in process-test
 * support, where the test framework drives the lifecycle).
 */
public class MultiCamundaLifecycleEventProducer implements SmartLifecycle {

  private final CamundaClientRegistry registry;
  private final ApplicationEventPublisher publisher;
  private boolean running = false;

  public MultiCamundaLifecycleEventProducer(
      final CamundaClientRegistry registry, final ApplicationEventPublisher publisher) {
    this.registry = registry;
    this.publisher = publisher;
  }

  @Override
  public void start() {
    registry
        .clientNames()
        .forEach(
            name ->
                publisher.publishEvent(
                    new CamundaClientCreatedSpringEvent(this, registry.get(name), name)));
    running = true;
  }

  @Override
  public void stop() {
    registry
        .clientNames()
        .forEach(
            name ->
                publisher.publishEvent(
                    new CamundaClientClosingSpringEvent(this, registry.get(name), name)));
    running = false;
  }

  @Override
  public boolean isRunning() {
    return running;
  }
}
