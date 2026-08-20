/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.read.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.db.rdbms.write.domain.GlobalListenerDbModel;
import io.camunda.db.rdbms.write.domain.GlobalListenerDbModel.GlobalListenerDbModelBuilder;
import io.camunda.search.entities.GlobalListenerEntity;
import io.camunda.search.entities.GlobalListenerSource;
import io.camunda.search.entities.GlobalListenerType;
import java.util.List;
import org.junit.jupiter.api.Test;

public class GlobalListenerEntityMapperTest {

  @Test
  public void testToEntity() {
    // Given
    final GlobalListenerDbModel dbModel =
        new GlobalListenerDbModelBuilder()
            .listenerId("my-listener")
            .type("io.camunda.zeebe:userTask")
            .retries(3)
            .eventTypes(List.of("start", "complete"))
            .afterNonGlobal(false)
            .priority(10)
            .source(GlobalListenerSource.CONFIGURATION)
            .listenerType(GlobalListenerType.USER_TASK)
            .build();

    // When
    final GlobalListenerEntity entity = GlobalListenerEntityMapper.toEntity(dbModel);

    // Then
    assertThat(entity.id()).isNotNull();
    assertThat(entity.listenerId()).isEqualTo("my-listener");
    assertThat(entity.type()).isEqualTo("io.camunda.zeebe:userTask");
    assertThat(entity.retries()).isEqualTo(3);
    assertThat(entity.eventTypes()).containsExactly("start", "complete");
    assertThat(entity.afterNonGlobal()).isFalse();
    assertThat(entity.priority()).isEqualTo(10);
    assertThat(entity.source()).isEqualTo(GlobalListenerSource.CONFIGURATION);
    assertThat(entity.listenerType()).isEqualTo(GlobalListenerType.USER_TASK);
  }

  @Test
  public void testToEntityWithNullValues() {
    // Given
    final GlobalListenerDbModel dbModel =
        new GlobalListenerDbModelBuilder()
            .listenerId(null)
            .type(null)
            .retries(3)
            .eventTypes(List.of())
            .afterNonGlobal(false)
            .priority(10)
            .source(GlobalListenerSource.CONFIGURATION)
            .listenerType(GlobalListenerType.USER_TASK)
            .build();

    // When
    final GlobalListenerEntity entity = GlobalListenerEntityMapper.toEntity(dbModel);

    // Then
    assertThat(entity.listenerId())
        .isEqualTo(""); // Oracle treats empty strings as NULL, mapper converts back to ""
    assertThat(entity.type())
        .isEqualTo(""); // Oracle treats empty strings as NULL, mapper converts back to ""
    assertThat(entity.retries()).isEqualTo(3);
    assertThat(entity.source()).isEqualTo(GlobalListenerSource.CONFIGURATION);
    assertThat(entity.listenerType()).isEqualTo(GlobalListenerType.USER_TASK);
  }

  @Test
  public void testToEntityWithNullModel() {
    // When
    final GlobalListenerEntity entity = GlobalListenerEntityMapper.toEntity(null);

    // Then
    assertThat(entity).isNull();
  }
}
