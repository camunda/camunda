/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mapping.http.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.gateway.protocol.model.ProcessDefinitionResumptionInstructionById;
import io.camunda.gateway.protocol.model.ProcessDefinitionResumptionInstructionByKey;
import io.camunda.gateway.protocol.model.ProcessDefinitionResumptionVersionResult;
import io.camunda.gateway.protocol.model.ProcessDefinitionSuspensionInstructionById;
import io.camunda.gateway.protocol.model.ProcessDefinitionSuspensionInstructionByKey;
import io.camunda.gateway.protocol.model.ProcessDefinitionSuspensionVersionResult.OutcomeEnum;
import io.camunda.service.ProcessDefinitionServices.ProcessDefinitionSuspensionRequest;
import io.camunda.zeebe.protocol.impl.record.value.processdefinition.ProcessDefinitionSuspensionRecord;
import io.camunda.zeebe.protocol.impl.record.value.processdefinition.ProcessDefinitionSuspensionResolvedVersion;
import io.camunda.zeebe.protocol.record.value.ProcessDefinitionSuspensionOutcome;
import io.camunda.zeebe.util.Either;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.http.ProblemDetail;

class ProcessDefinitionMapperTest {

  private static final String DEFAULT_TENANT = "<default>";

  private final ProcessDefinitionMapper mapper = new ProcessDefinitionMapper();

  @Test
  void shouldMapSuspensionByKey() {
    // given
    final var instruction =
        ProcessDefinitionSuspensionInstructionByKey.Builder.create()
            .processDefinitionKey("123")
            .build();

    // when
    final var result = mapper.toSuspendProcessDefinitions(instruction, false);

    // then
    assertThat(result.isRight()).isTrue();
    assertThat(result.get())
        .isEqualTo(new ProcessDefinitionSuspensionRequest(123L, "", -1, null, false));
  }

  @Test
  void shouldMapSuspensionByKeyWithIncludeProcessInstances() {
    // given
    final var instruction =
        ProcessDefinitionSuspensionInstructionByKey.Builder.create()
            .processDefinitionKey("123")
            .includeProcessInstances(true)
            .build();

    // when
    final var result = mapper.toSuspendProcessDefinitions(instruction, false);

    // then
    assertThat(result.isRight()).isTrue();
    assertThat(result.get())
        .isEqualTo(new ProcessDefinitionSuspensionRequest(123L, "", -1, null, true));
  }

  @Test
  void shouldMapSuspensionByIdWithoutVersionToAllVersions() {
    // given
    final var instruction =
        ProcessDefinitionSuspensionInstructionById.Builder.create()
            .processDefinitionId("invoice")
            .build();

    // when
    final var result = mapper.toSuspendProcessDefinitions(instruction, false);

    // then
    assertThat(result.isRight()).isTrue();
    assertThat(result.get())
        .isEqualTo(
            new ProcessDefinitionSuspensionRequest(-1L, "invoice", -1, DEFAULT_TENANT, false));
  }

  @Test
  void shouldMapSuspensionByIdWithVersionTenantAndIncludeProcessInstances() {
    // given
    final var instruction =
        ProcessDefinitionSuspensionInstructionById.Builder.create()
            .processDefinitionId("invoice")
            .includeProcessInstances(true)
            .processDefinitionVersion(3)
            .tenantId("tenant-a")
            .build();

    // when
    final var result = mapper.toSuspendProcessDefinitions(instruction, true);

    // then
    assertThat(result.isRight()).isTrue();
    assertThat(result.get())
        .isEqualTo(new ProcessDefinitionSuspensionRequest(-1L, "invoice", 3, "tenant-a", true));
  }

  @Test
  void shouldMapResumptionByKey() {
    // given
    final var instruction =
        ProcessDefinitionResumptionInstructionByKey.Builder.create()
            .processDefinitionKey("456")
            .build();

    // when
    final var result = mapper.toResumeProcessDefinitions(instruction, false);

    // then
    assertThat(result.isRight()).isTrue();
    assertThat(result.get())
        .isEqualTo(new ProcessDefinitionSuspensionRequest(456L, "", -1, null, false));
  }

  @Test
  void shouldMapResumptionByKeyWithIncludeProcessInstances() {
    // given
    final var instruction =
        ProcessDefinitionResumptionInstructionByKey.Builder.create()
            .processDefinitionKey("456")
            .includeProcessInstances(true)
            .build();

    // when
    final var result = mapper.toResumeProcessDefinitions(instruction, false);

    // then
    assertThat(result.isRight()).isTrue();
    assertThat(result.get())
        .isEqualTo(new ProcessDefinitionSuspensionRequest(456L, "", -1, null, true));
  }

  @Test
  void shouldMapResumptionByIdWithoutVersionToAllVersions() {
    // given
    final var instruction =
        ProcessDefinitionResumptionInstructionById.Builder.create()
            .processDefinitionId("invoice")
            .build();

    // when
    final var result = mapper.toResumeProcessDefinitions(instruction, false);

    // then
    assertThat(result.isRight()).isTrue();
    assertThat(result.get())
        .isEqualTo(
            new ProcessDefinitionSuspensionRequest(-1L, "invoice", -1, DEFAULT_TENANT, false));
  }

  @Test
  void shouldMapResumptionByIdWithVersionTenantAndIncludeProcessInstances() {
    // given
    final var instruction =
        ProcessDefinitionResumptionInstructionById.Builder.create()
            .processDefinitionId("invoice")
            .includeProcessInstances(true)
            .processDefinitionVersion(2)
            .tenantId("tenant-a")
            .build();

    // when
    final var result = mapper.toResumeProcessDefinitions(instruction, true);

    // then
    assertThat(result.isRight()).isTrue();
    assertThat(result.get())
        .isEqualTo(new ProcessDefinitionSuspensionRequest(-1L, "invoice", 2, "tenant-a", true));
  }

  @Test
  void shouldRejectInvalidKey() {
    // given
    final var instruction =
        ProcessDefinitionSuspensionInstructionByKey.Builder.create()
            .processDefinitionKey("not-a-key")
            .build();

    // when
    final var result = mapper.toSuspendProcessDefinitions(instruction, false);

    // then
    assertBadRequestMentioning(result, "processDefinitionKey");
  }

  @Test
  void shouldRejectIllegalProcessDefinitionId() {
    // given
    final var instruction =
        ProcessDefinitionResumptionInstructionById.Builder.create()
            .processDefinitionId("1 invalid id")
            .build();

    // when
    final var result = mapper.toResumeProcessDefinitions(instruction, false);

    // then
    assertBadRequestMentioning(result, "processDefinitionId");
  }

  @Test
  void shouldRejectNonPositiveVersion() {
    // given
    final var instruction =
        ProcessDefinitionSuspensionInstructionById.Builder.create()
            .processDefinitionId("invoice")
            .processDefinitionVersion(0)
            .build();

    // when
    final var result = mapper.toSuspendProcessDefinitions(instruction, false);

    // then
    assertBadRequestMentioning(result, "processDefinitionVersion");
  }

  @Test
  void shouldRejectMissingTenantWhenMultiTenancyIsEnabled() {
    // given
    final var instruction =
        ProcessDefinitionSuspensionInstructionById.Builder.create()
            .processDefinitionId("invoice")
            .build();

    // when
    final var result = mapper.toSuspendProcessDefinitions(instruction, true);

    // then
    assertBadRequestMentioning(result, "no tenant identifier was provided");
  }

  @Test
  void shouldRejectNonDefaultTenantWhenMultiTenancyIsDisabled() {
    // given
    final var instruction =
        ProcessDefinitionSuspensionInstructionById.Builder.create()
            .processDefinitionId("invoice")
            .tenantId("tenant-a")
            .build();

    // when
    final var result = mapper.toSuspendProcessDefinitions(instruction, false);

    // then
    assertBadRequestMentioning(result, "tenant-a");
  }

  @Test
  void shouldMapSuspensionResultWithBatchOperationKey() {
    // given
    final var record =
        new ProcessDefinitionSuspensionRecord()
            .setBatchOperationKey(77L)
            .addResolvedVersion(
                new ProcessDefinitionSuspensionResolvedVersion()
                    .setProcessDefinitionKey(11L)
                    .setVersion(1)
                    .setOutcome(ProcessDefinitionSuspensionOutcome.SUSPENDED));

    // when
    final var result = mapper.toSuspensionResult(record);

    // then
    assertThat(result.getBatchOperationKey()).isEqualTo("77");
  }

  @Test
  void shouldMapSuspensionResultWithoutBatchOperationKey() {
    // given
    final var record =
        new ProcessDefinitionSuspensionRecord()
            .addResolvedVersion(
                new ProcessDefinitionSuspensionResolvedVersion()
                    .setProcessDefinitionKey(11L)
                    .setVersion(1)
                    .setOutcome(ProcessDefinitionSuspensionOutcome.SUSPENDED));

    // when
    final var result = mapper.toSuspensionResult(record);

    // then
    assertThat(result.getBatchOperationKey()).isNull();
  }

  @ParameterizedTest
  @EnumSource(
      value = ProcessDefinitionSuspensionOutcome.class,
      names = {"SUSPENDED", "ALREADY_SUSPENDED", "DRAINING"})
  void shouldMapSuspensionOutcome(final ProcessDefinitionSuspensionOutcome outcome) {
    // given
    final var record =
        new ProcessDefinitionSuspensionRecord()
            .addResolvedVersion(
                new ProcessDefinitionSuspensionResolvedVersion()
                    .setProcessDefinitionKey(11L)
                    .setVersion(1)
                    .setOutcome(outcome));

    // when
    final var result = mapper.toSuspensionResult(record);

    // then
    assertThat(result.getProcessDefinitions())
        .singleElement()
        .satisfies(
            v -> {
              assertThat(v.getProcessDefinitionKey()).isEqualTo("11");
              assertThat(v.getProcessDefinitionVersion()).isEqualTo(1);
              assertThat(v.getOutcome()).isEqualTo(OutcomeEnum.fromValue(outcome.name()));
            });
  }

  @Test
  void shouldMapResumptionResultWithBatchOperationKey() {
    // given
    final var record =
        new ProcessDefinitionSuspensionRecord()
            .setBatchOperationKey(78L)
            .addResolvedVersion(
                new ProcessDefinitionSuspensionResolvedVersion()
                    .setProcessDefinitionKey(11L)
                    .setVersion(1)
                    .setOutcome(ProcessDefinitionSuspensionOutcome.RESUMED));

    // when
    final var result = mapper.toResumptionResult(record);

    // then
    assertThat(result.getBatchOperationKey()).isEqualTo("78");
  }

  @Test
  void shouldMapResumptionResultWithoutBatchOperationKey() {
    // given
    final var record =
        new ProcessDefinitionSuspensionRecord()
            .addResolvedVersion(
                new ProcessDefinitionSuspensionResolvedVersion()
                    .setProcessDefinitionKey(11L)
                    .setVersion(1)
                    .setOutcome(ProcessDefinitionSuspensionOutcome.RESUMED));

    // when
    final var result = mapper.toResumptionResult(record);

    // then
    assertThat(result.getBatchOperationKey()).isNull();
  }

  @ParameterizedTest
  @EnumSource(
      value = ProcessDefinitionSuspensionOutcome.class,
      names = {"RESUMED", "ALREADY_RESUMED", "DRAINING"})
  void shouldMapResumptionOutcome(final ProcessDefinitionSuspensionOutcome outcome) {
    // given
    final var record =
        new ProcessDefinitionSuspensionRecord()
            .addResolvedVersion(
                new ProcessDefinitionSuspensionResolvedVersion()
                    .setProcessDefinitionKey(11L)
                    .setVersion(1)
                    .setOutcome(outcome));

    // when
    final var result = mapper.toResumptionResult(record);

    // then
    assertThat(result.getProcessDefinitions())
        .singleElement()
        .satisfies(
            v -> {
              assertThat(v.getProcessDefinitionKey()).isEqualTo("11");
              assertThat(v.getProcessDefinitionVersion()).isEqualTo(1);
              assertThat(v.getOutcome())
                  .isEqualTo(
                      ProcessDefinitionResumptionVersionResult.OutcomeEnum.fromValue(
                          outcome.name()));
            });
  }

  private static void assertBadRequestMentioning(
      final Either<ProblemDetail, ?> result, final String text) {
    assertThat(result.isLeft()).isTrue();
    assertThat(result.getLeft().getStatus()).isEqualTo(400);
    assertThat(result.getLeft().getDetail()).contains(text);
  }
}
