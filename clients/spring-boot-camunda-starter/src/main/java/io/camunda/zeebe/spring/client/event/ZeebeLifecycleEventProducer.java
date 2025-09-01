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
package io.camunda.zeebe.spring.client.event;

import io.camunda.zeebe.client.ZeebeClient;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.SmartLifecycle;

@Deprecated(forRemoval = true, since = "8.6")
public class ZeebeLifecycleEventProducer implements SmartLifecycle {

  protected boolean running = false;

  private final ApplicationEventPublisher publisher;

  private final ZeebeClient client;

  public ZeebeLifecycleEventProducer(
      final ZeebeClient client, final ApplicationEventPublisher publisher) {
    this.client = client;
    this.publisher = publisher;
  }

  @Override
  public void start() {
    publisher.publishEvent(new ZeebeClientCreatedEvent(this, client));
    running = true;
  }

  @Override
  public void stop() {
    publisher.publishEvent(new ZeebeClientClosingEvent(this, client));
    running = false;
  }

  @Override
  public boolean isRunning() {
    return running;
  }
}
