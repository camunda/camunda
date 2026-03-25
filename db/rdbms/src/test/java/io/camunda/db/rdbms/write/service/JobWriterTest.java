/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.write.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.db.rdbms.config.VendorDatabaseProperties;
import io.camunda.db.rdbms.sql.JobMapper;
import io.camunda.db.rdbms.write.domain.JobDbModel;
import io.camunda.db.rdbms.write.queue.ExecutionQueue;
import io.camunda.db.rdbms.write.queue.QueueItem;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class JobWriterTest {

  private ExecutionQueue executionQueue;
  private VendorDatabaseProperties vendorDatabaseProperties;
  private JobWriter writer;

  @BeforeEach
  void setUp() {
    executionQueue = mock(ExecutionQueue.class);
    vendorDatabaseProperties = mock(VendorDatabaseProperties.class);
    when(vendorDatabaseProperties.errorMessageSize()).thenReturn(10);
    when(vendorDatabaseProperties.charColumnMaxBytes()).thenReturn(null);
    writer = new JobWriter(executionQueue, mock(JobMapper.class), vendorDatabaseProperties);
  }

  @Test
  void shouldTruncateErrorMessageOnCreate() {
    // given
    final var job = buildJobWithErrorMessage("this is a very long error message");

    // when
    writer.create(job);

    // then
    final var captor = ArgumentCaptor.forClass(QueueItem.class);
    verify(executionQueue).executeInQueue(captor.capture());

    final var enqueuedJob = (JobDbModel) captor.getValue().parameter();
    assertThat(enqueuedJob.errorMessage()).hasSize(10);
    assertThat(enqueuedJob.errorMessage()).isEqualTo("this is a ");
  }

  @Test
  void shouldTruncateErrorMessageOnUpdate() {
    // given
    final var job = buildJobWithErrorMessage("this is a very long error message");

    // when
    writer.update(job);

    // then
    final var captor = ArgumentCaptor.forClass(QueueItem.class);
    verify(executionQueue).executeInQueue(captor.capture());

    final var enqueuedJob = (JobDbModel) captor.getValue().parameter();
    assertThat(enqueuedJob.errorMessage()).hasSize(10);
    assertThat(enqueuedJob.errorMessage()).isEqualTo("this is a ");
  }

  private static JobDbModel buildJobWithErrorMessage(final String errorMessage) {
    return new JobDbModel.Builder()
        .jobKey(1L)
        .processInstanceKey(2L)
        .processDefinitionKey(3L)
        .elementInstanceKey(4L)
        .elementId("elementId")
        .type("type")
        .retries(1)
        .worker("worker")
        .deadline(OffsetDateTime.now())
        .tenantId("tenantId")
        .errorMessage(errorMessage)
        .build();
  }
}
