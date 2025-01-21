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
package io.camunda.spring.client.event;

import io.camunda.client.CamundaClient;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.SmartLifecycle;

public class CamundaLifecycleEventProducer implements SmartLifecycle {

  protected boolean running = false;

  private final ApplicationEventPublisher publisher;

  private final CamundaClient client;

  public CamundaLifecycleEventProducer(
      final CamundaClient client, final ApplicationEventPublisher publisher) {
    this.client = client;
    this.publisher = publisher;
  }

  @Override
  public void start() {
    publisher.publishEvent(new CamundaClientCreatedEvent(this, client));
    running = true;
  }

  @Override
  public void stop() {
    publisher.publishEvent(new CamundaClientClosingEvent(this, client));
    running = false;
  }

  @Override
  public boolean isRunning() {
    return running;
  }
}
