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
import static org.mockito.Mockito.when;

import io.camunda.client.CamundaClient;
import io.camunda.client.spring.bean.CamundaClientRegistry;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationEventPublisher;

/**
 * Unit tests for {@link MultiCamundaLifecycleEventProducer}: it publishes one created/closing event
 * per configured client, each carrying the client's configured name.
 */
public class MultiCamundaLifecycleEventProducerTest {

  private CamundaClientRegistry registry;
  private final List<ApplicationEvent> published = new java.util.ArrayList<>();
  private ApplicationEventPublisher publisher;
  private CamundaClient clientA;
  private CamundaClient clientB;

  @BeforeEach
  void setUp() {
    published.clear();
    registry = mock(CamundaClientRegistry.class);
    publisher = event -> published.add((ApplicationEvent) event);
    clientA = mock(CamundaClient.class);
    clientB = mock(CamundaClient.class);
    final Set<String> names = new LinkedHashSet<>(List.of("a", "b"));
    when(registry.clientNames()).thenReturn(names);
    when(registry.get("a")).thenReturn(clientA);
    when(registry.get("b")).thenReturn(clientB);
  }

  @Test
  void shouldPublishCreatedEventPerClientOnStart() {
    // given
    final MultiCamundaLifecycleEventProducer producer =
        new MultiCamundaLifecycleEventProducer(registry, publisher);

    // when
    producer.start();

    // then - one created event per client, each carrying its name and client
    assertThat(published).hasSize(2).allMatch(CamundaClientCreatedSpringEvent.class::isInstance);
    assertThat(published)
        .extracting(e -> ((CamundaClientCreatedSpringEvent) e).getClientName())
        .containsExactlyInAnyOrder("a", "b");
    assertThat(published)
        .extracting(e -> ((CamundaClientCreatedSpringEvent) e).getClient())
        .containsExactlyInAnyOrder(clientA, clientB);
    assertThat(producer.isRunning()).isTrue();
  }

  @Test
  void shouldPublishClosingEventPerClientOnStop() {
    // given
    final MultiCamundaLifecycleEventProducer producer =
        new MultiCamundaLifecycleEventProducer(registry, publisher);
    producer.start();
    published.clear();

    // when
    producer.stop();

    // then - one closing event per client, each carrying its name
    assertThat(published).hasSize(2).allMatch(CamundaClientClosingSpringEvent.class::isInstance);
    assertThat(published)
        .extracting(e -> ((CamundaClientClosingSpringEvent) e).getClientName())
        .containsExactlyInAnyOrder("a", "b");
    assertThat(producer.isRunning()).isFalse();
  }
}
