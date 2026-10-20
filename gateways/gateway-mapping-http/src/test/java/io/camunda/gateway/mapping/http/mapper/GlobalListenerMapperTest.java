/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mapping.http.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.gateway.mapping.http.validator.GlobalListenerRequestValidator;
import io.camunda.gateway.protocol.model.CreateGlobalTaskListenerRequest;
import io.camunda.gateway.protocol.model.GlobalListenerSourceEnum;
import io.camunda.gateway.protocol.model.GlobalTaskListenerEventTypeEnum;
import io.camunda.gateway.protocol.model.UpdateGlobalTaskListenerRequest;
import io.camunda.security.validation.IdentifierValidator;
import io.camunda.zeebe.protocol.impl.record.value.globallistener.GlobalListenerRecord;
import io.camunda.zeebe.protocol.record.value.GlobalListenerSource;
import io.camunda.zeebe.protocol.record.value.GlobalListenerType;
import java.util.List;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class GlobalListenerMapperTest {

  private static final Pattern ID_PATTERN = Pattern.compile("^[a-zA-Z0-9_~@.+-]+$");

  private final GlobalListenerMapper mapper =
      new GlobalListenerMapper(
          new GlobalListenerRequestValidator(new IdentifierValidator(ID_PATTERN, ID_PATTERN)));

  @Test
  void shouldMapCreateRequestToUserTaskRecordOwnedByTheApi() {
    // given
    final var request =
        CreateGlobalTaskListenerRequest.Builder.create()
            .id("my-listener")
            .type("job-type")
            .eventTypes(
                List.of(
                    GlobalTaskListenerEventTypeEnum.CREATING,
                    GlobalTaskListenerEventTypeEnum.COMPLETING))
            .retries(7)
            .afterNonGlobal(true)
            .priority(12)
            .build();

    // when
    final var result = mapper.toGlobalTaskListenerCreateRequest(request);

    // then
    assertThat(result.isRight()).isTrue();
    final var record = result.get();
    assertThat(record.getId()).isEqualTo("my-listener");
    assertThat(record.getType()).isEqualTo("job-type");
    assertThat(record.getEventTypes()).containsExactly("creating", "completing");
    assertThat(record.getRetries()).isEqualTo(7);
    assertThat(record.isAfterNonGlobal()).isTrue();
    assertThat(record.getPriority()).isEqualTo(12);
    assertThat(record.getListenerType()).isEqualTo(GlobalListenerType.USER_TASK);
    assertThat(record.getSource()).isEqualTo(GlobalListenerSource.API);
  }

  @Test
  void shouldKeepRecordDefaultsForOmittedOptionalCreateFields() {
    // given
    final var request =
        CreateGlobalTaskListenerRequest.Builder.create()
            .id("my-listener")
            .type("job-type")
            .eventTypes(List.of(GlobalTaskListenerEventTypeEnum.ALL))
            .build();

    // when
    final var result = mapper.toGlobalTaskListenerCreateRequest(request);

    // then
    assertThat(result.isRight()).isTrue();
    final var record = result.get();
    assertThat(record.getRetries()).isEqualTo(GlobalListenerRecord.DEFAULT_RETRIES);
    assertThat(record.getPriority()).isEqualTo(GlobalListenerRecord.DEFAULT_PRIORITY);
    assertThat(record.isAfterNonGlobal()).isFalse();
  }

  @Test
  void shouldReturnProblemInsteadOfRecordForInvalidCreateRequest() {
    // given
    final var request =
        CreateGlobalTaskListenerRequest.Builder.create()
            .id("my-listener")
            .type("job-type")
            .eventTypes(List.of())
            .build();

    // when
    final var result = mapper.toGlobalTaskListenerCreateRequest(request);

    // then
    assertThat(result.isLeft()).isTrue();
    assertThat(result.getLeft().getStatus()).isEqualTo(400);
  }

  @Test
  void shouldTakeUpdateIdFromThePathAndMapBodyFields() {
    // given
    final var request =
        UpdateGlobalTaskListenerRequest.Builder.create()
            .type("new-job-type")
            .eventTypes(List.of(GlobalTaskListenerEventTypeEnum.UPDATING))
            .retries(5)
            .priority(80)
            .build();

    // when
    final var result = mapper.toGlobalTaskListenerUpdateRequest("my-listener", request);

    // then
    assertThat(result.isRight()).isTrue();
    final var record = result.get();
    assertThat(record.getId()).isEqualTo("my-listener");
    assertThat(record.getType()).isEqualTo("new-job-type");
    assertThat(record.getEventTypes()).containsExactly("updating");
    assertThat(record.getRetries()).isEqualTo(5);
    assertThat(record.getPriority()).isEqualTo(80);
    assertThat(record.getListenerType()).isEqualTo(GlobalListenerType.USER_TASK);
    assertThat(record.getSource()).isEqualTo(GlobalListenerSource.API);
  }

  @Test
  void shouldReturnProblemInsteadOfRecordForInvalidUpdateRequest() {
    // given
    final var request =
        UpdateGlobalTaskListenerRequest.Builder.create()
            .type("job-type")
            .eventTypes(List.of(GlobalTaskListenerEventTypeEnum.UPDATING))
            .build();

    // when
    final var result = mapper.toGlobalTaskListenerUpdateRequest("$invalid", request);

    // then
    assertThat(result.isLeft()).isTrue();
  }

  @Test
  void shouldMapGetAndDeleteToIdentifyingUserTaskRecord() {
    // when
    final var get = mapper.toGlobalTaskListenerGetRequest("my-listener");
    final var delete = mapper.toGlobalTaskListenerDeleteRequest("my-listener");

    // then
    assertThat(get.isRight()).isTrue();
    assertThat(get.get().getId()).isEqualTo("my-listener");
    assertThat(get.get().getListenerType()).isEqualTo(GlobalListenerType.USER_TASK);
    assertThat(delete.isRight()).isTrue();
    assertThat(delete.get().getId()).isEqualTo("my-listener");
    assertThat(delete.get().getListenerType()).isEqualTo(GlobalListenerType.USER_TASK);
  }

  @Test
  void shouldReturnProblemForInvalidIdOnGetAndDelete() {
    // when / then
    assertThat(mapper.toGlobalTaskListenerGetRequest("$invalid").isLeft()).isTrue();
    assertThat(mapper.toGlobalTaskListenerDeleteRequest("$invalid").isLeft()).isTrue();
  }

  @Test
  void shouldMapRecordToResponse() {
    // given
    final var record =
        new GlobalListenerRecord()
            .setId("my-listener")
            .setType("job-type")
            .setRetries(7)
            .setEventTypes(List.of("creating", "canceling"))
            .setAfterNonGlobal(true)
            .setPriority(12)
            .setSource(GlobalListenerSource.CONFIGURATION)
            .setListenerType(GlobalListenerType.USER_TASK);

    // when
    final var response = mapper.toGlobalListenerResponse(record);

    // then
    assertThat(response.getId()).isEqualTo("my-listener");
    assertThat(response.getType()).isEqualTo("job-type");
    assertThat(response.getRetries()).isEqualTo(7);
    assertThat(response.getEventTypes())
        .containsExactly(
            GlobalTaskListenerEventTypeEnum.CREATING, GlobalTaskListenerEventTypeEnum.CANCELING);
    assertThat(response.getAfterNonGlobal()).isTrue();
    assertThat(response.getPriority()).isEqualTo(12);
    assertThat(response.getSource()).isEqualTo(GlobalListenerSourceEnum.CONFIGURATION);
  }
}
