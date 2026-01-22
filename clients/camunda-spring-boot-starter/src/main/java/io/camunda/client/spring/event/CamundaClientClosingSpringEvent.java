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
import io.camunda.client.event.CamundaClientClosingEvent;
import org.springframework.context.ApplicationEvent;

/**
 * Emitted when the CamundaClient is about to close. Typically, during application shutdown, but
 * maybe more often in test case or never if the CamundaClient is disabled, see {@link
 * CamundaClientCreatedSpringEvent} for more details
 */
public class CamundaClientClosingSpringEvent extends ApplicationEvent
    implements CamundaClientClosingEvent {

  public final CamundaClient client;
  private final String clientName;

  public CamundaClientClosingSpringEvent(final Object source, final CamundaClient client) {
    this(source, client, null);
  }

  public CamundaClientClosingSpringEvent(
      final Object source, final CamundaClient client, final String clientName) {
    super(source);
    this.client = client;
    this.clientName = clientName;
  }

  @Override
  public CamundaClient getClient() {
    return client;
  }

  /**
   * Returns the configured name of this client, or null if running in single-client mode.
   *
   * @return the client name, or null
   */
  public String getClientName() {
    return clientName;
  }
}
