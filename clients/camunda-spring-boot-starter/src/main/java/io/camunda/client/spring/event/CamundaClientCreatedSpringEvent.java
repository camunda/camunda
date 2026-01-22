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
import io.camunda.client.event.CamundaClientCreatedEvent;
import org.springframework.context.ApplicationEvent;

/**
 * Event which is triggered when the CamundaClient was created. This can be used to register further
 * work that should be done, like starting job workers or doing deployments.
 *
 * <p>In a normal production application this event is simply fired once during startup when the
 * CamundaClient is created and thus ready to use. However, in test cases it might be fired multiple
 * times, as every test case gets its own dedicated engine also leading to new CamundaClients being
 * created (at least logically, as the CamundaClient Spring bean might simply be a proxy always
 * pointing to the right client automatically to avoid problems with @Autowire).
 *
 * <p>Furthermore, when `camunda.client.enabled=false`, the event might not be fired ever
 */
public class CamundaClientCreatedSpringEvent extends ApplicationEvent
    implements CamundaClientCreatedEvent {

  public final CamundaClient client;
  private final String clientName;

  public CamundaClientCreatedSpringEvent(final Object source, final CamundaClient client) {
    this(source, client, null);
  }

  public CamundaClientCreatedSpringEvent(
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
