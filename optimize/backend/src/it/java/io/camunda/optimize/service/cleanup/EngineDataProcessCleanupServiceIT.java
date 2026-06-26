/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.cleanup;

import static io.camunda.optimize.service.util.importing.ZeebeConstants.ZEEBE_DEFAULT_TENANT_ID;
import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.collect.ImmutableList;
import io.camunda.optimize.AbstractBrokerlessZeebeCCSMIT;
import io.camunda.optimize.dto.optimize.ProcessInstanceConstants;
import io.camunda.optimize.dto.optimize.ProcessInstanceDto;
import io.camunda.optimize.dto.optimize.datasource.ZeebeDataSourceDto;
import io.camunda.optimize.dto.optimize.query.variable.SimpleProcessVariableDto;
import io.camunda.optimize.service.security.util.LocalDateUtil;
import io.camunda.optimize.service.util.configuration.cleanup.CleanupConfiguration;
import io.camunda.optimize.service.util.configuration.cleanup.CleanupMode;
import io.camunda.optimize.service.util.configuration.cleanup.ProcessCleanupConfiguration;
import io.camunda.optimize.service.util.configuration.cleanup.ProcessDefinitionCleanupConfiguration;
import io.github.netmikey.logunit.api.LogCapturer;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.apache.commons.text.RandomStringGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

public class EngineDataProcessCleanupServiceIT extends AbstractBrokerlessZeebeCCSMIT {
  private static final RandomStringGenerator KEY_GENERATOR =
      new RandomStringGenerator.Builder().withinRange('a', 'z').get();

  @RegisterExtension
  LogCapturer cleanupServiceLogs = LogCapturer.create().captureForType(CleanupService.class);

  @BeforeEach
  public void enableProcessCleanup() {
    embeddedOptimizeExtension
        .getConfigurationService()
        .getCleanupServiceConfiguration()
        .getProcessDataCleanupConfiguration()
        .setEnabled(true);
  }

  @Test
  public void shouldDeleteProcessInstanceWhenCleanupModeAll() {
    // given
    final var processDefinitionKey = KEY_GENERATOR.generate(10);

    getProcessDataCleanupConfiguration().setCleanupMode(CleanupMode.ALL);

    final var now = LocalDateUtil.getCurrentDateTime();
    final var endDateForCleanup = getEndTimeLessThanGlobalTtl();

    final List<ProcessInstanceDto> instancesToGetCleanedUp =
        List.of(
            processInstanceWithEndDate(processDefinitionKey, endDateForCleanup),
            processInstanceWithEndDate(processDefinitionKey, endDateForCleanup));
    final ProcessInstanceDto unaffectedProcessInstanceForSameDefinition =
        processInstanceWithEndDate(processDefinitionKey, now);

    final List<ProcessInstanceDto> allInstances =
        ImmutableList.<ProcessInstanceDto>builder()
            .addAll(instancesToGetCleanedUp)
            .add(unaffectedProcessInstanceForSameDefinition)
            .build();

    persistProcessInstances(allInstances);

    // when
    embeddedOptimizeExtension.getCleanupScheduler().runCleanup();

    databaseIntegrationTestExtension.refreshAllOptimizeIndices();

    // then
    assertNoProcessInstanceDataExists(instancesToGetCleanedUp);

    assertProcessInstanceDataExists(List.of(unaffectedProcessInstanceForSameDefinition));
  }

  @Test
  public void shouldDeleteProcessInstanceWhenCleanupModeAllWithMultipleDefinitions() {
    // given
    final var processDefinitionKey1 = KEY_GENERATOR.generate(10);
    final var processDefinitionKey2 = KEY_GENERATOR.generate(10);

    getProcessDataCleanupConfiguration().setCleanupMode(CleanupMode.ALL);

    final var now = LocalDateUtil.getCurrentDateTime();
    final var endDateForCleanup = getEndTimeLessThanGlobalTtl();

    final List<ProcessInstanceDto> instancesToGetCleanedUp =
        List.of(
            processInstanceWithEndDate(processDefinitionKey1, endDateForCleanup),
            processInstanceWithEndDate(processDefinitionKey1, endDateForCleanup),
            processInstanceWithEndDate(processDefinitionKey1, endDateForCleanup),
            processInstanceWithEndDate(processDefinitionKey1, endDateForCleanup),
            processInstanceWithEndDate(processDefinitionKey2, endDateForCleanup),
            processInstanceWithEndDate(processDefinitionKey2, endDateForCleanup));
    final List<ProcessInstanceDto> unaffectedProcessInstances =
        List.of(
            processInstanceWithEndDate(processDefinitionKey1, now),
            processInstanceWithEndDate(processDefinitionKey1, now),
            processInstanceWithEndDate(processDefinitionKey1, now),
            processInstanceWithEndDate(processDefinitionKey2, now));

    final List<ProcessInstanceDto> allInstances =
        ImmutableList.<ProcessInstanceDto>builder()
            .addAll(instancesToGetCleanedUp)
            .addAll(unaffectedProcessInstances)
            .build();

    persistProcessInstances(allInstances);

    // when
    embeddedOptimizeExtension.getCleanupScheduler().runCleanup();
    databaseIntegrationTestExtension.refreshAllOptimizeIndices();

    // then
    assertNoProcessInstanceDataExists(instancesToGetCleanedUp);

    assertProcessInstanceDataExists(unaffectedProcessInstances);
  }

  @Test
  public void shouldNotDeleteProcessInstanceWhenCleanupModeAllDisabled() {
    // given
    final var processDefinitionKey = KEY_GENERATOR.generate(10);

    getProcessDataCleanupConfiguration().setEnabled(false);
    getProcessDataCleanupConfiguration().setCleanupMode(CleanupMode.ALL);
    final var endDateForCleanup = getEndTimeLessThanGlobalTtl();

    final List<ProcessInstanceDto> instancesPastCleanupTTL =
        List.of(
            processInstanceWithEndDate(processDefinitionKey, endDateForCleanup),
            processInstanceWithEndDate(processDefinitionKey, endDateForCleanup));

    persistProcessInstances(instancesPastCleanupTTL);

    // when
    embeddedOptimizeExtension.getCleanupScheduler().runCleanup();
    databaseIntegrationTestExtension.refreshAllOptimizeIndices();

    // then
    assertProcessInstanceDataExists(instancesPastCleanupTTL);
  }

  @Test
  public void shouldDeleteProcessInstanceWhenCleanupModeAllWithCustomBatchSize() {
    // given
    final var processDefinitionKey = KEY_GENERATOR.generate(10);

    getProcessDataCleanupConfiguration().setCleanupMode(CleanupMode.ALL);
    getProcessDataCleanupConfiguration().setBatchSize(1);

    final var now = LocalDateUtil.getCurrentDateTime();
    final var endDateForCleanup = getEndTimeLessThanGlobalTtl();

    final List<ProcessInstanceDto> instancesToGetCleanedUp =
        List.of(
            processInstanceWithEndDate(processDefinitionKey, endDateForCleanup),
            processInstanceWithEndDate(processDefinitionKey, endDateForCleanup));
    final ProcessInstanceDto unaffectedProcessInstanceForSameDefinition =
        processInstanceWithEndDate(processDefinitionKey, now);

    final List<ProcessInstanceDto> allInstances =
        ImmutableList.<ProcessInstanceDto>builder()
            .addAll(instancesToGetCleanedUp)
            .add(unaffectedProcessInstanceForSameDefinition)
            .build();

    persistProcessInstances(allInstances);

    // when
    embeddedOptimizeExtension.getCleanupScheduler().runCleanup();

    databaseIntegrationTestExtension.refreshAllOptimizeIndices();

    // then
    assertNoProcessInstanceDataExists(instancesToGetCleanedUp);

    assertProcessInstanceDataExists(List.of(unaffectedProcessInstanceForSameDefinition));
  }

  @Test
  public void shouldNotDeleteProcessInstanceWhenCleanupModeAllAndSpecificKeyTtl() {
    // given
    final var processDefinitionKey1 = KEY_GENERATOR.generate(10);
    final var processDefinitionKey2 = KEY_GENERATOR.generate(10);

    getProcessDataCleanupConfiguration().setCleanupMode(CleanupMode.ALL);
    configureHigherProcessSpecificTtl(processDefinitionKey2);

    final var endDateForCleanup = getEndTimeLessThanGlobalTtl();
    final List<ProcessInstanceDto> instancesToGetCleanedUp =
        List.of(
            processInstanceWithEndDate(processDefinitionKey1, endDateForCleanup),
            processInstanceWithEndDate(processDefinitionKey1, endDateForCleanup));

    final List<ProcessInstanceDto> instancesOfDefinitionWithHigherTtl =
        List.of(
            processInstanceWithEndDate(processDefinitionKey2, endDateForCleanup),
            processInstanceWithEndDate(processDefinitionKey2, endDateForCleanup));

    final List<ProcessInstanceDto> allInstances =
        ImmutableList.<ProcessInstanceDto>builder()
            .addAll(instancesToGetCleanedUp)
            .addAll(instancesOfDefinitionWithHigherTtl)
            .build();

    persistProcessInstances(allInstances);

    // when
    embeddedOptimizeExtension.getCleanupScheduler().runCleanup();
    databaseIntegrationTestExtension.refreshAllOptimizeIndices();

    // then
    assertNoProcessInstanceDataExists(instancesToGetCleanedUp);
    assertProcessInstanceDataExists(instancesOfDefinitionWithHigherTtl);
  }

  @Test
  public void shouldRemoveVariablesWhenCleanupModeVariables() {
    // given
    final var processDefinitionKey = KEY_GENERATOR.generate(10);
    getProcessDataCleanupConfiguration().setCleanupMode(CleanupMode.VARIABLES);

    final var now = LocalDateUtil.getCurrentDateTime();
    final var endDateForCleanup = getEndTimeLessThanGlobalTtl();

    final List<ProcessInstanceDto> instancesToGetCleanedUp =
        List.of(
            processInstanceWithEndDate(processDefinitionKey, endDateForCleanup),
            processInstanceWithEndDate(processDefinitionKey, endDateForCleanup));
    final ProcessInstanceDto unaffectedProcessInstanceForSameDefinition =
        processInstanceWithEndDate(processDefinitionKey, now);

    final List<ProcessInstanceDto> allInstances =
        ImmutableList.<ProcessInstanceDto>builder()
            .addAll(instancesToGetCleanedUp)
            .add(unaffectedProcessInstanceForSameDefinition)
            .build();

    persistProcessInstances(allInstances);

    // when
    embeddedOptimizeExtension.getCleanupScheduler().runCleanup();
    databaseIntegrationTestExtension.refreshAllOptimizeIndices();

    // then
    assertVariablesEmptyInProcessInstances(instancesToGetCleanedUp);

    assertProcessInstanceDataExists(List.of(unaffectedProcessInstanceForSameDefinition));
  }

  @Test
  public void shouldRemoveVariablesForSpecificKeyCleanupMode() {
    // given
    final var processDefinitionKey = KEY_GENERATOR.generate(10);
    getProcessDataCleanupConfiguration().setCleanupMode(CleanupMode.ALL);
    getCleanupConfiguration()
        .getProcessDataCleanupConfiguration()
        .getProcessDefinitionSpecificConfiguration()
        .put(
            processDefinitionKey,
            ProcessDefinitionCleanupConfiguration.builder()
                .cleanupMode(CleanupMode.VARIABLES)
                .build());

    final var endDateForCleanup = getEndTimeLessThanGlobalTtl();
    final List<ProcessInstanceDto> instancesOfDefinitionWithVariableMode =
        List.of(
            processInstanceWithEndDate(processDefinitionKey, endDateForCleanup),
            processInstanceWithEndDate(processDefinitionKey, endDateForCleanup));

    persistProcessInstances(instancesOfDefinitionWithVariableMode);

    // when
    embeddedOptimizeExtension.getCleanupScheduler().runCleanup();
    databaseIntegrationTestExtension.refreshAllOptimizeIndices();

    // then
    assertVariablesEmptyInProcessInstances(instancesOfDefinitionWithVariableMode);
  }

  @Test
  public void shouldLogNoMatchingProcessDefinitionWarning() {
    // given I have a key specific config
    final var processDefinitionKey = KEY_GENERATOR.generate(10);
    final String configuredKey = "myMistypedKey";

    getProcessDataCleanupConfiguration().setCleanupMode(CleanupMode.VARIABLES);
    getProcessDataCleanupConfiguration()
        .getProcessDefinitionSpecificConfiguration()
        .put(configuredKey, new ProcessDefinitionCleanupConfiguration(CleanupMode.VARIABLES));

    // and deploy processes with different keys
    final var now = LocalDateUtil.getCurrentDateTime();
    final var endDateForCleanup = getEndTimeLessThanGlobalTtl();

    final List<ProcessInstanceDto> instancesToGetCleanedUp =
        List.of(
            processInstanceWithEndDate(processDefinitionKey, endDateForCleanup),
            processInstanceWithEndDate(processDefinitionKey, endDateForCleanup));
    final ProcessInstanceDto unaffectedProcessInstanceForSameDefinition =
        processInstanceWithEndDate(processDefinitionKey, now);

    final List<ProcessInstanceDto> allInstances =
        ImmutableList.<ProcessInstanceDto>builder()
            .addAll(instancesToGetCleanedUp)
            .add(unaffectedProcessInstanceForSameDefinition)
            .build();

    persistProcessInstances(allInstances);

    // when
    embeddedOptimizeExtension.getCleanupScheduler().runCleanup();
    databaseIntegrationTestExtension.refreshAllOptimizeIndices();

    // then data clear up has succeeded as expected
    assertVariablesEmptyInProcessInstances(instancesToGetCleanedUp);

    assertProcessInstanceDataExists(List.of(unaffectedProcessInstanceForSameDefinition));
    // and the misconfigured process is logged
    cleanupServiceLogs.assertContains(
        String.format(
            "History Cleanup Configuration contains definition keys for which there is no "
                + "definition imported yet. The keys without a match in the database are: [%s]",
            configuredKey));
  }

  private void assertNoProcessInstanceDataExists(final List<ProcessInstanceDto> instances) {
    final var ids = instances.stream().map(ProcessInstanceDto::getProcessInstanceId).toList();
    final var instancesInDb = databaseIntegrationTestExtension.getProcessInstancesById(ids);
    assertThat(instancesInDb).isEmpty();
  }

  private void assertVariablesEmptyInProcessInstances(final List<ProcessInstanceDto> instances) {
    final var ids = instances.stream().map(ProcessInstanceDto::getProcessInstanceId).toList();
    assertThat(databaseIntegrationTestExtension.getProcessInstancesById(ids))
        .hasSameSizeAs(instances)
        .allMatch(processInstanceDto -> processInstanceDto.getVariables().isEmpty());
  }

  private void assertProcessInstanceDataExists(final List<ProcessInstanceDto> instances) {
    final var ids = instances.stream().map(ProcessInstanceDto::getProcessInstanceId).toList();
    final var instancesInDb = databaseIntegrationTestExtension.getProcessInstancesById(ids);
    assertThat(instancesInDb).containsAll(instances);
  }

  private CleanupConfiguration getCleanupConfiguration() {
    return embeddedOptimizeExtension.getConfigurationService().getCleanupServiceConfiguration();
  }

  private ProcessCleanupConfiguration getProcessDataCleanupConfiguration() {
    return getCleanupConfiguration().getProcessDataCleanupConfiguration();
  }

  private void configureHigherProcessSpecificTtl(final String processDefinitionKey) {
    getCleanupConfiguration()
        .getProcessDataCleanupConfiguration()
        .getProcessDefinitionSpecificConfiguration()
        .put(
            processDefinitionKey,
            ProcessDefinitionCleanupConfiguration.builder()
                .cleanupMode(CleanupMode.ALL)
                // higher ttl than default
                .ttl(getCleanupConfiguration().getTtl().plusYears(5L))
                .build());
  }

  private OffsetDateTime getEndTimeLessThanGlobalTtl() {
    return LocalDateUtil.getCurrentDateTime()
        .minus(getCleanupConfiguration().getTtl())
        .minusSeconds(1);
  }

  private static ProcessInstanceDto processInstanceWithEndDate(
      final String processDefinitionKey, final OffsetDateTime endDate) {
    final OffsetDateTime startDate = endDate.minusHours(1);
    final long duration = Duration.between(startDate, endDate).toMillis();
    return ProcessInstanceDto.builder()
        .processInstanceId(UUID.randomUUID().toString())
        .processDefinitionKey(processDefinitionKey)
        .processDefinitionVersion("1")
        .processDefinitionId(processDefinitionKey + ":1:1")
        .tenantId(ZEEBE_DEFAULT_TENANT_ID)
        .state(ProcessInstanceConstants.COMPLETED_STATE)
        .dataSource(new ZeebeDataSourceDto("test-source", 1))
        .startDate(startDate)
        .endDate(endDate)
        .duration(duration)
        .variables(
            List.of(
                SimpleProcessVariableDto.builder()
                    .id("var1")
                    .name("Variable1")
                    .value(List.of("one"))
                    .build()))
        .build();
  }
}
