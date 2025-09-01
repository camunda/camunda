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
import org.springframework.context.ApplicationEvent;

/**
 * Emitted when the CamundaClient is about to close. Typically, during application shutdown, but
 * maybe more often in test case or never if the CamundaClient is disabled, see {@link
 * CamundaClientCreatedEvent} for more details
 */
public class CamundaClientClosingEvent extends ApplicationEvent {

  public final CamundaClient client;

  public CamundaClientClosingEvent(final Object source, final CamundaClient client) {
    super(source);
    this.client = client;
  }

  public CamundaClient getClient() {
    return client;
  }
}
