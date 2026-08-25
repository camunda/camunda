/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.rest;

import static io.camunda.optimize.service.util.configuration.ConfigurationServiceConstants.CCSM_PROFILE;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.optimize.AbstractCCSMIT;
import io.camunda.optimize.dto.optimize.ProcessDefinitionOptimizeDto;
import io.camunda.optimize.dto.optimize.query.job.EntityType;
import io.camunda.optimize.dto.optimize.query.job.JobRegistryEntryDto;
import io.camunda.optimize.dto.optimize.query.job.JobStatus;
import io.camunda.optimize.dto.optimize.query.job.JobType;
import io.camunda.optimize.service.db.reader.JobRegistryReader;
import io.camunda.optimize.service.db.writer.JobRegistryWriter;
import io.camunda.optimize.service.db.writer.ProcessDefinitionWriter;
import jakarta.ws.rs.core.Response;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.http.HttpStatus;

public class PublicApiProcessDefinitionDeletionIT extends AbstractCCSMIT {

  private static final String TEST_ACCESS_TOKEN = "test-access-token";

  @Override
  protected void startAndUseNewOptimizeInstance() {
    // see PublicApiVariableLabelsIT for rationale for pinning cslEnabled=false here
    startAndUseNewOptimizeInstance(Map.of("optimize.security.csl.enabled", "false"), CCSM_PROFILE);
  }

  @BeforeEach
  public void configurePublicApiToken() {
    startAndUseNewOptimizeInstance();
    embeddedOptimizeExtension
        .getConfigurationService()
        .getOptimizeApiConfiguration()
        .setAccessToken(TEST_ACCESS_TOKEN);
  }

  @Test
  public void shouldReturn202AndQueueJobForValidRequest() {
    // given
    final String processDefinitionKey = "100000000000001";
    seedProcessDefinition(processDefinitionKey);

    // when
    final Response response =
        embeddedOptimizeExtension
            .getRequestExecutor()
            .buildDeleteProcessDefinitionDataRequest(processDefinitionKey)
            .withBearerToken(TEST_ACCESS_TOKEN)
            .execute();

    // then
    assertThat(response.getStatus()).isEqualTo(HttpStatus.ACCEPTED.value());
    final JobRegistryEntryDto entry =
        embeddedOptimizeExtension
            .getBean(JobRegistryReader.class)
            .findLastByJobTypeAndEntityId(
                JobType.DELETE, EntityType.PROCESS_DEFINITION, processDefinitionKey)
            .orElseThrow();
    assertThat(entry.getStatus()).isEqualTo(JobStatus.QUEUED);
    assertThat(entry.getEntityType()).isEqualTo(EntityType.PROCESS_DEFINITION);
  }

  @Test
  public void shouldReturn400WhenKeyIsNotNumeric() {
    // when
    final Response response =
        embeddedOptimizeExtension
            .getRequestExecutor()
            .buildDeleteProcessDefinitionDataRequest("not-a-number")
            .withBearerToken(TEST_ACCESS_TOKEN)
            .execute();

    // then
    assertThat(response.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
  }

  @Test
  public void shouldReturn404WhenDefinitionNotFound() {
    // when
    final Response response =
        embeddedOptimizeExtension
            .getRequestExecutor()
            .buildDeleteProcessDefinitionDataRequest("100000000000002")
            .withBearerToken(TEST_ACCESS_TOKEN)
            .execute();

    // then
    assertThat(response.getStatus()).isEqualTo(HttpStatus.NOT_FOUND.value());
  }

  @ParameterizedTest
  @EnumSource(
      value = JobStatus.class,
      names = {"QUEUED", "COMPLETED"})
  public void shouldReturn409WhenBlockingEntryAlreadyExists(final JobStatus blockingStatus) {
    // given
    final String processDefinitionKey = "100000000000003";
    seedProcessDefinition(processDefinitionKey);
    seedJobRegistryEntry(processDefinitionKey, blockingStatus);

    // when
    final Response response =
        embeddedOptimizeExtension
            .getRequestExecutor()
            .buildDeleteProcessDefinitionDataRequest(processDefinitionKey)
            .withBearerToken(TEST_ACCESS_TOKEN)
            .execute();

    // then
    assertThat(response.getStatus()).isEqualTo(HttpStatus.CONFLICT.value());
  }

  @Test
  public void shouldReturn409OnBackToBackDeleteRequestsForSameKey() {
    // given
    final String processDefinitionKey = "100000000000006";
    seedProcessDefinition(processDefinitionKey);

    // when
    final Response firstResponse =
        embeddedOptimizeExtension
            .getRequestExecutor()
            .buildDeleteProcessDefinitionDataRequest(processDefinitionKey)
            .withBearerToken(TEST_ACCESS_TOKEN)
            .execute();
    final Response secondResponse =
        embeddedOptimizeExtension
            .getRequestExecutor()
            .buildDeleteProcessDefinitionDataRequest(processDefinitionKey)
            .withBearerToken(TEST_ACCESS_TOKEN)
            .execute();

    // then
    assertThat(firstResponse.getStatus()).isEqualTo(HttpStatus.ACCEPTED.value());
    assertThat(secondResponse.getStatus()).isEqualTo(HttpStatus.CONFLICT.value());
  }

  @Test
  public void shouldReturn202AndQueueNewJobWhenExistingEntryHasFailedStatus() {
    // given
    final String processDefinitionKey = "100000000000004";
    seedProcessDefinition(processDefinitionKey);
    seedJobRegistryEntry(processDefinitionKey, JobStatus.FAILED);

    // when
    final Response response =
        embeddedOptimizeExtension
            .getRequestExecutor()
            .buildDeleteProcessDefinitionDataRequest(processDefinitionKey)
            .withBearerToken(TEST_ACCESS_TOKEN)
            .execute();

    // then
    assertThat(response.getStatus()).isEqualTo(HttpStatus.ACCEPTED.value());
  }

  @Test
  public void shouldReturn401WhenNoTokenProvided() {
    // when
    final Response response =
        embeddedOptimizeExtension
            .getRequestExecutor()
            .buildDeleteProcessDefinitionDataRequest("100000000000005")
            .execute();

    // then
    assertThat(response.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED.value());
  }

  private void seedJobRegistryEntry(final String processDefinitionKey, final JobStatus status) {
    final JobRegistryWriter jobRegistryWriter =
        embeddedOptimizeExtension.getBean(JobRegistryWriter.class);
    final JobRegistryEntryDto entry =
        jobRegistryWriter.createJobEntry(
            JobType.DELETE, EntityType.PROCESS_DEFINITION, processDefinitionKey);
    jobRegistryWriter.updateJobStatus(entry.getId(), status, null);
    databaseIntegrationTestExtension.refreshAllOptimizeIndices();
  }

  private void seedProcessDefinition(final String processDefinitionKey) {
    embeddedOptimizeExtension
        .getBean(ProcessDefinitionWriter.class)
        .importProcessDefinitions(
            List.of(
                ProcessDefinitionOptimizeDto.builder()
                    .id(processDefinitionKey)
                    .key("aBpmnProcessId")
                    .version("1")
                    .name("aProcessName")
                    .bpmn20Xml("<definitions/>")
                    .build()));
    databaseIntegrationTestExtension.refreshAllOptimizeIndices();
  }
}
