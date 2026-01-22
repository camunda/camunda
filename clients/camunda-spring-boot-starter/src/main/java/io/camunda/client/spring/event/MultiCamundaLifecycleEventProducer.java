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

import io.camunda.client.CamundaClient;
import io.camunda.client.spring.bean.CamundaClientRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.SmartLifecycle;

/**
 * Lifecycle event producer for multi-client configuration.
 *
 * <p>This producer fires {@link CamundaClientCreatedSpringEvent} and {@link
 * CamundaClientClosingSpringEvent} for each client in the registry. This ensures that all
 * lifecycle-aware components (like job workers and deployment processors) are started on ALL
 * configured clients.
 *
 * <p>Each client receives its own set of events, allowing workers to be registered on every
 * configured Camunda cluster.
 */
public class MultiCamundaLifecycleEventProducer implements SmartLifecycle {

  private static final Logger LOG =
      LoggerFactory.getLogger(MultiCamundaLifecycleEventProducer.class);

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
    LOG.info(
        "Starting multi-client lifecycle for {} clients: {}",
        registry.size(),
        registry.getClientNames());

    registry
        .getClientNames()
        .forEach(
            name -> {
              final CamundaClient client = registry.getClient(name);
              LOG.debug("Publishing CamundaClientCreatedSpringEvent for client '{}'", name);
              publisher.publishEvent(new CamundaClientCreatedSpringEvent(this, client));
            });

    running = true;
  }

  @Override
  public void stop() {
    LOG.info(
        "Stopping multi-client lifecycle for {} clients: {}",
        registry.size(),
        registry.getClientNames());

    registry
        .getClientNames()
        .forEach(
            name -> {
              final CamundaClient client = registry.getClient(name);
              LOG.debug("Publishing CamundaClientClosingSpringEvent for client '{}'", name);
              publisher.publishEvent(new CamundaClientClosingSpringEvent(this, client));
            });

    running = false;
  }

  @Override
  public boolean isRunning() {
    return running;
  }

  @Override
  public int getPhase() {
    // Same phase as CamundaLifecycleEventProducer (default is 0)
    return 0;
  }
}
