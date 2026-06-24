/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.transformers.entity;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.webapps.schema.entities.globallistener.GlobalListenerEntity;
import io.camunda.webapps.schema.entities.globallistener.GlobalListenerSource;
import io.camunda.webapps.schema.entities.globallistener.GlobalListenerType;
import java.util.List;
import org.junit.jupiter.api.Test;

class GlobalListenerEntityTransformerTest {

  private final GlobalListenerEntityTransformer transformer = new GlobalListenerEntityTransformer();

  @Test
  void shouldTransformEntityToSearchEntity() {
    // given
    final GlobalListenerEntity entity = new GlobalListenerEntity();
    entity.setId("USER_TASK-my-listener");
    entity.setListenerId("my-listener");
    entity.setType("my-job");
    entity.setRetries(3);
    entity.setEventTypes(List.of("all"));
    entity.setAfterNonGlobal(true);
    entity.setPriority(50);
    entity.setSource(GlobalListenerSource.API);
    entity.setListenerType(GlobalListenerType.USER_TASK);

    // when
    final var searchEntity = transformer.apply(entity);
    assertThat(searchEntity).isNotNull();
    assertThat(searchEntity.id()).isEqualTo("USER_TASK-my-listener");
    assertThat(searchEntity.listenerId()).isEqualTo("my-listener");
    assertThat(searchEntity.type()).isEqualTo("my-job");
    assertThat(searchEntity.retries()).isEqualTo(3);
    assertThat(searchEntity.eventTypes()).isEqualTo(List.of("all"));
    assertThat(searchEntity.afterNonGlobal()).isTrue();
    assertThat(searchEntity.priority()).isEqualTo(50);
    assertThat(searchEntity.source())
        .isEqualTo(io.camunda.search.entities.GlobalListenerSource.API);
    assertThat(searchEntity.listenerType())
        .isEqualTo(io.camunda.search.entities.GlobalListenerType.USER_TASK);
  }
}
