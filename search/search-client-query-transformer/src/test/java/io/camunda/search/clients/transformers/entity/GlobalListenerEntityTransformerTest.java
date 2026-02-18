/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.transformers.entity;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.search.entities.GlobalListenerSource;
import io.camunda.search.entities.GlobalListenerType;
import java.util.List;
import org.junit.jupiter.api.Test;

class GlobalListenerEntityTransformerTest {

  private final GlobalListenerEntityTransformer transformer = new GlobalListenerEntityTransformer();

  @Test
  void shouldTransformEntityToSearchEntity() {
    // given
    final var entity = new io.camunda.webapps.schema.entities.globallistener.GlobalListenerEntity();
    entity.setId("gl-1");
    entity.setListenerId("listener-1");
    entity.setType("io.camunda.MyListener");
    entity.setEventTypes(List.of("start", "end"));
    entity.setRetries(3);
    entity.setAfterNonGlobal(true);
    entity.setPriority(10);
    entity.setSource(
        io.camunda.webapps.schema.entities.globallistener.GlobalListenerEntity.GlobalListenerSource
            .PROCESS);
    entity.setListenerType(
        io.camunda.webapps.schema.entities.globallistener.GlobalListenerEntity.GlobalListenerType
            .EXECUTION_LISTENER);

    // when
    final var searchEntity = transformer.apply(entity);

    // then
    assertThat(searchEntity).isNotNull();
    assertThat(searchEntity.id()).isEqualTo("gl-1");
    assertThat(searchEntity.listenerId()).isEqualTo("listener-1");
    assertThat(searchEntity.type()).isEqualTo("io.camunda.MyListener");
    assertThat(searchEntity.eventTypes()).containsExactly("start", "end");
    assertThat(searchEntity.retries()).isEqualTo(3);
    assertThat(searchEntity.afterNonGlobal()).isTrue();
    assertThat(searchEntity.priority()).isEqualTo(10);
    assertThat(searchEntity.source()).isEqualTo(GlobalListenerSource.PROCESS);
    assertThat(searchEntity.listenerType()).isEqualTo(GlobalListenerType.EXECUTION_LISTENER);
  }

  @Test
  void shouldTransformEntityWithTaskListenerType() {
    // given
    final var entity = new io.camunda.webapps.schema.entities.globallistener.GlobalListenerEntity();
    entity.setId("gl-2");
    entity.setListenerId("listener-2");
    entity.setType("io.camunda.MyTaskListener");
    entity.setEventTypes(List.of("create", "complete"));
    entity.setRetries(1);
    entity.setAfterNonGlobal(false);
    entity.setPriority(5);
    entity.setSource(
        io.camunda.webapps.schema.entities.globallistener.GlobalListenerEntity.GlobalListenerSource
            .USER_TASK);
    entity.setListenerType(
        io.camunda.webapps.schema.entities.globallistener.GlobalListenerEntity.GlobalListenerType
            .TASK_LISTENER);

    // when
    final var searchEntity = transformer.apply(entity);

    // then
    assertThat(searchEntity).isNotNull();
    assertThat(searchEntity.id()).isEqualTo("gl-2");
    assertThat(searchEntity.listenerType()).isEqualTo(GlobalListenerType.TASK_LISTENER);
    assertThat(searchEntity.source()).isEqualTo(GlobalListenerSource.USER_TASK);
    assertThat(searchEntity.afterNonGlobal()).isFalse();
  }
}
