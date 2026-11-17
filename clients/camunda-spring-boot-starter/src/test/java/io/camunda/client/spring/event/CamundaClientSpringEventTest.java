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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import io.camunda.client.CamundaClient;
import io.camunda.client.event.CamundaClientCreatedEvent;
import org.junit.jupiter.api.Test;

/**
 * Verifies that the no-name event constructors fall back to {@link
 * CamundaClientCreatedEvent#DEFAULT_CLIENT_NAME} rather than {@code null}, so consumers always
 * receive a client name.
 */
public class CamundaClientSpringEventTest {

  private final CamundaClient client = mock(CamundaClient.class);

  @Test
  void shouldDefaultCreatedEventClientNameWhenUnnamed() {
    // when
    final CamundaClientCreatedSpringEvent event = new CamundaClientCreatedSpringEvent(this, client);

    // then
    assertThat(event.getClientName()).isEqualTo(CamundaClientCreatedEvent.DEFAULT_CLIENT_NAME);
  }

  @Test
  void shouldDefaultClosingEventClientNameWhenUnnamed() {
    // when
    final CamundaClientClosingSpringEvent event = new CamundaClientClosingSpringEvent(this, client);

    // then
    assertThat(event.getClientName()).isEqualTo(CamundaClientCreatedEvent.DEFAULT_CLIENT_NAME);
  }

  @Test
  void shouldKeepExplicitClientName() {
    // when
    final CamundaClientCreatedSpringEvent event =
        new CamundaClientCreatedSpringEvent(this, client, "finance");

    // then
    assertThat(event.getClientName()).isEqualTo("finance");
  }
}
