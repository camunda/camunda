/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.read.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.db.rdbms.write.domain.JobDbModel;
import io.camunda.db.rdbms.write.domain.JobDbModel.Builder;
import io.camunda.search.entities.JobEntity;
import io.camunda.search.entities.JobEntity.JobKind;
import io.camunda.search.entities.JobEntity.JobState;
import io.camunda.search.entities.JobEntity.ListenerEventType;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import org.assertj.core.data.TemporalUnitWithinOffset;
import org.junit.jupiter.api.Test;

public class JobEntityMapperTest {

  @Test
  public void testToEntity() {
    // Given
    final JobDbModel jobDbModel =
        new Builder()
            .jobKey(1L)
            .type("testJobType")
            .worker("testWorker")
            .state(JobState.CREATED)
            .kind(JobKind.BPMN_ELEMENT)
            .listenerEventType(ListenerEventType.START)
            .retries(3)
            .isDenied(true)
            .deniedReason("testDeniedReason")
            .hasFailedWithRetriesLeft(false)
            .errorCode("testErrorCode")
            .errorMessage("testErrorMessage")
            .customHeaders(Map.of("headerKey", "headerValue"))
            .deadline(OffsetDateTime.now().plusDays(1))
            .endTime(OffsetDateTime.now().plusDays(2))
            .processDefinitionId("processDefinitionId")
            .processDefinitionKey(1L)
            .processInstanceKey(1L)
            .elementId("elementBpmnId")
            .elementInstanceKey(1L)
            .tenantId("tenantId")
            .build();

    // When
    final JobEntity entity = JobEntityMapper.toEntity(jobDbModel);

    // Then
    assertThat(entity)
        .usingRecursiveComparison()
        .ignoringFields("deadline", "endTime", "customHeaders")
        .isEqualTo(jobDbModel);

    assertThat(entity.deadline())
        .isCloseTo(jobDbModel.deadline(), new TemporalUnitWithinOffset(1, ChronoUnit.MILLIS));
    assertThat(entity.endTime())
        .isCloseTo(jobDbModel.endTime(), new TemporalUnitWithinOffset(1, ChronoUnit.MILLIS));
    assertThat(entity.customHeaders()).isEqualTo(Map.of("headerKey", "headerValue"));
  }

  @Test
  public void testToEntityWithNullValues() {
    // Given
    final JobDbModel jobDbModel =
        new Builder()
            .jobKey(1L)
            .type(null)
            .worker(null)
            .state(JobState.COMPLETED)
            .kind(JobKind.BPMN_ELEMENT)
            .listenerEventType(ListenerEventType.START)
            .retries(0)
            .isDenied(false)
            .deniedReason(null)
            .hasFailedWithRetriesLeft(false)
            .errorCode(null)
            .errorMessage(null)
            .customHeaders(null)
            .deadline(null)
            .endTime(null)
            .processDefinitionId(null)
            .processDefinitionKey(1L)
            .processInstanceKey(1L)
            .elementId(null)
            .elementInstanceKey(1L)
            .tenantId(null)
            .creationTime(OffsetDateTime.now())
            .lastUpdateTime(OffsetDateTime.now())
            .build();

    // When
    final JobEntity entity = JobEntityMapper.toEntity(jobDbModel);

    // Then
    assertThat(entity.jobKey()).isNotNull();
    assertThat(entity.type()).isNull();
    assertThat(entity.worker()).isNull();
    assertThat(entity.state()).isNotNull();
    assertThat(entity.kind()).isNotNull();
    assertThat(entity.listenerEventType()).isNotNull();
    assertThat(entity.deniedReason()).isNull();
    assertThat(entity.isDenied()).isFalse();
    assertThat(entity.errorCode()).isNull();
    assertThat(entity.errorMessage()).isNull();
    assertThat(entity.retries()).isZero();
    assertThat(entity.hasFailedWithRetriesLeft()).isFalse();
    assertThat(entity.customHeaders()).isEmpty();
    assertThat(entity.deadline()).isNull();
    assertThat(entity.endTime()).isNull();
    assertThat(entity.processDefinitionId()).isNull();
    assertThat(entity.elementId()).isNull();
    assertThat(entity.tenantId()).isNull();
    assertThat(entity.creationTime()).isNotNull();
    assertThat(entity.lastUpdateTime()).isNotNull();
  }
}
