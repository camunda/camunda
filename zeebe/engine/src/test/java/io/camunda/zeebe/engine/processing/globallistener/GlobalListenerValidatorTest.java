/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.globallistener;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.state.globallistener.GlobalListenersState;
import io.camunda.zeebe.protocol.impl.record.value.globallistener.GlobalListenerRecord;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.value.GlobalListenerType;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GlobalListenerValidatorTest {

  private final GlobalListenerValidator validator = new GlobalListenerValidator();

  @Mock private GlobalListenersState globalListenersState;

  @Nested
  class EventTypeValidation {

    @Test
    void shouldAcceptStartForExecutionListener() {
      // given
      final var record =
          new GlobalListenerRecord()
              .setId("el-1")
              .setListenerType(GlobalListenerType.EXECUTION)
              .setEventTypes(List.of("start"));

      // when
      final var result = validator.validEventTypes(record);

      // then
      assertThat(result.isRight()).isTrue();
    }

    @Test
    void shouldAcceptEndForExecutionListener() {
      // given
      final var record =
          new GlobalListenerRecord()
              .setId("el-1")
              .setListenerType(GlobalListenerType.EXECUTION)
              .setEventTypes(List.of("end"));

      // when
      final var result = validator.validEventTypes(record);

      // then
      assertThat(result.isRight()).isTrue();
    }

    @Test
    void shouldAcceptCancelForExecutionListener() {
      // given — "cancel" is valid per OpenAPI spec GlobalExecutionListenerEventTypeEnum
      final var record =
          new GlobalListenerRecord()
              .setId("el-1")
              .setListenerType(GlobalListenerType.EXECUTION)
              .setEventTypes(List.of("cancel"));

      // when
      final var result = validator.validEventTypes(record);

      // then
      assertThat(result.isRight()).isTrue();
    }

    @Test
    void shouldAcceptMultipleValidExecutionListenerEventTypes() {
      // given
      final var record =
          new GlobalListenerRecord()
              .setId("el-1")
              .setListenerType(GlobalListenerType.EXECUTION)
              .setEventTypes(List.of("start", "end"));

      // when
      final var result = validator.validEventTypes(record);

      // then
      assertThat(result.isRight()).isTrue();
    }

    @Test
    void shouldAcceptAllThreeExecutionListenerEventTypes() {
      // given — start, end, cancel are all valid per API spec
      final var record =
          new GlobalListenerRecord()
              .setId("el-1")
              .setListenerType(GlobalListenerType.EXECUTION)
              .setEventTypes(List.of("start", "end", "cancel"));

      // when
      final var result = validator.validEventTypes(record);

      // then
      assertThat(result.isRight()).isTrue();
    }

    @Test
    void shouldRejectAllEventTypeForExecutionListener() {
      // given — "all" is only valid for task listeners, not execution listeners
      final var record =
          new GlobalListenerRecord()
              .setId("el-1")
              .setListenerType(GlobalListenerType.EXECUTION)
              .setEventTypes(List.of("all"));

      // when
      final var result = validator.validEventTypes(record);

      // then
      assertThat(result.isLeft()).isTrue();
      assertThat(result.getLeft().type()).isEqualTo(RejectionType.INVALID_ARGUMENT);
      assertThat(result.getLeft().reason()).contains("all");
    }

    @Test
    void shouldAcceptAllEventTypeForTaskListener() {
      // given
      final var record =
          new GlobalListenerRecord()
              .setId("tl-1")
              .setListenerType(GlobalListenerType.USER_TASK)
              .setEventTypes(List.of("all"));

      // when
      final var result = validator.validEventTypes(record);

      // then
      assertThat(result.isRight()).isTrue();
    }

    @Test
    void shouldRejectTaskListenerEventTypeForExecutionListener() {
      // given — "creating" is a task listener event type, not execution
      final var record =
          new GlobalListenerRecord()
              .setId("el-1")
              .setListenerType(GlobalListenerType.EXECUTION)
              .setEventTypes(List.of("creating"));

      // when
      final var result = validator.validEventTypes(record);

      // then
      assertThat(result.isLeft()).isTrue();
      assertThat(result.getLeft().type()).isEqualTo(RejectionType.INVALID_ARGUMENT);
      assertThat(result.getLeft().reason()).contains("Invalid event types");
      assertThat(result.getLeft().reason()).contains("creating");
    }

    @Test
    void shouldRejectUnknownEventTypeForExecutionListener() {
      // given
      final var record =
          new GlobalListenerRecord()
              .setId("el-1")
              .setListenerType(GlobalListenerType.EXECUTION)
              .setEventTypes(List.of("unknown"));

      // when
      final var result = validator.validEventTypes(record);

      // then
      assertThat(result.isLeft()).isTrue();
      assertThat(result.getLeft().type()).isEqualTo(RejectionType.INVALID_ARGUMENT);
    }

    @Test
    void shouldAcceptCreatingForTaskListener() {
      // given
      final var record =
          new GlobalListenerRecord()
              .setId("tl-1")
              .setListenerType(GlobalListenerType.USER_TASK)
              .setEventTypes(List.of("creating", "completing"));

      // when
      final var result = validator.validEventTypes(record);

      // then
      assertThat(result.isRight()).isTrue();
    }

    @Test
    void shouldRejectExecutionListenerEventTypeForTaskListener() {
      // given — "start" is an execution listener event type, not task
      final var record =
          new GlobalListenerRecord()
              .setId("tl-1")
              .setListenerType(GlobalListenerType.USER_TASK)
              .setEventTypes(List.of("start"));

      // when
      final var result = validator.validEventTypes(record);

      // then
      assertThat(result.isLeft()).isTrue();
      assertThat(result.getLeft().type()).isEqualTo(RejectionType.INVALID_ARGUMENT);
      assertThat(result.getLeft().reason()).contains("start");
    }

    @Test
    void shouldRejectMixedValidAndInvalidEventTypes() {
      // given — "start" is valid, "completing" is not for execution listeners
      final var record =
          new GlobalListenerRecord()
              .setId("el-1")
              .setListenerType(GlobalListenerType.EXECUTION)
              .setEventTypes(List.of("start", "completing"));

      // when
      final var result = validator.validEventTypes(record);

      // then
      assertThat(result.isLeft()).isTrue();
      assertThat(result.getLeft().reason()).contains("completing");
    }
  }

  @Nested
  class FieldValidation {

    @Test
    void shouldRejectMissingId() {
      // given
      final var record =
          new GlobalListenerRecord().setListenerType(GlobalListenerType.EXECUTION).setId("");

      // when
      final var result = validator.idProvided(record);

      // then
      assertThat(result.isLeft()).isTrue();
      assertThat(result.getLeft().type()).isEqualTo(RejectionType.INVALID_ARGUMENT);
      assertThat(result.getLeft().reason()).contains("Missing id");
    }

    @Test
    void shouldRejectMissingType() {
      // given
      final var record =
          new GlobalListenerRecord()
              .setListenerType(GlobalListenerType.EXECUTION)
              .setId("el-1")
              .setType("");

      // when
      final var result = validator.typeProvided(record);

      // then
      assertThat(result.isLeft()).isTrue();
      assertThat(result.getLeft().type()).isEqualTo(RejectionType.INVALID_ARGUMENT);
      assertThat(result.getLeft().reason()).contains("Missing type");
    }

    @Test
    void shouldRejectEmptyEventTypes() {
      // given
      final var record =
          new GlobalListenerRecord()
              .setListenerType(GlobalListenerType.EXECUTION)
              .setId("el-1")
              .setEventTypes(List.of());

      // when
      final var result = validator.eventTypesProvided(record);

      // then
      assertThat(result.isLeft()).isTrue();
      assertThat(result.getLeft().type()).isEqualTo(RejectionType.INVALID_ARGUMENT);
      assertThat(result.getLeft().reason()).contains("Missing event types");
    }

    @Test
    void shouldAcceptValidFields() {
      // given
      final var record =
          new GlobalListenerRecord()
              .setListenerType(GlobalListenerType.EXECUTION)
              .setId("el-1")
              .setType("audit-job")
              .setEventTypes(List.of("start"));

      // when/then
      assertThat(validator.idProvided(record).isRight()).isTrue();
      assertThat(validator.typeProvided(record).isRight()).isTrue();
      assertThat(validator.eventTypesProvided(record).isRight()).isTrue();
      assertThat(validator.validEventTypes(record).isRight()).isTrue();
    }
  }

  @Nested
  class ExistenceChecks {

    @Test
    void shouldResolveExistingListener() {
      // given
      final var record =
          new GlobalListenerRecord().setId("el-1").setListenerType(GlobalListenerType.EXECUTION);
      final var existing =
          new GlobalListenerRecord()
              .setId("el-1")
              .setGlobalListenerKey(42L)
              .setListenerType(GlobalListenerType.EXECUTION);
      Mockito.when(globalListenersState.getGlobalListener(GlobalListenerType.EXECUTION, "el-1"))
          .thenReturn(existing);

      // when
      final var result = validator.resolveExistingListener(record, globalListenersState);

      // then
      assertThat(result.isRight()).isTrue();
      assertThat(result.get().getGlobalListenerKey()).isEqualTo(42L);
    }

    @Test
    void shouldRejectNonExistingListenerOnResolve() {
      // given
      final var record =
          new GlobalListenerRecord().setId("el-1").setListenerType(GlobalListenerType.EXECUTION);
      Mockito.when(globalListenersState.getGlobalListener(GlobalListenerType.EXECUTION, "el-1"))
          .thenReturn(null);

      // when
      final var result = validator.resolveExistingListener(record, globalListenersState);

      // then
      assertThat(result.isLeft()).isTrue();
      assertThat(result.getLeft().type()).isEqualTo(RejectionType.NOT_FOUND);
    }

    @Test
    void shouldAcceptNewListener() {
      // given
      final var record =
          new GlobalListenerRecord().setId("el-1").setListenerType(GlobalListenerType.EXECUTION);
      Mockito.when(globalListenersState.getGlobalListener(GlobalListenerType.EXECUTION, "el-1"))
          .thenReturn(null);

      // when
      final var result = validator.listenerDoesNotExist(record, globalListenersState);

      // then
      assertThat(result.isRight()).isTrue();
    }

    @Test
    void shouldRejectDuplicateListener() {
      // given
      final var record =
          new GlobalListenerRecord().setId("el-1").setListenerType(GlobalListenerType.EXECUTION);
      final var existing = new GlobalListenerRecord().setId("el-1");
      Mockito.when(globalListenersState.getGlobalListener(GlobalListenerType.EXECUTION, "el-1"))
          .thenReturn(existing);

      // when
      final var result = validator.listenerDoesNotExist(record, globalListenersState);

      // then
      assertThat(result.isLeft()).isTrue();
      assertThat(result.getLeft().type()).isEqualTo(RejectionType.ALREADY_EXISTS);
    }
  }
}
