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

import io.camunda.client.CamundaClient;
import io.camunda.client.event.CamundaClientCreatedEvent;
import io.camunda.client.spring.configuration.CamundaAutoConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.event.EventListener;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest(classes = CamundaAutoConfiguration.class)
public class CamundaClientEventListenerTest {
  @MockitoBean CamundaClient client;

  @EventListener
  public void onClientCreated(final CamundaClientCreatedEvent event) {
    assertThat(event.getClient().equals(client));
  }

  @Test
  void shouldRun() {}
}
