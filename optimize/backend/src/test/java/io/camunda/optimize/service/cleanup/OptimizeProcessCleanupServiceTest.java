/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.cleanup;

import static java.util.stream.Collectors.toMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableList;
import io.camunda.optimize.dto.optimize.ProcessDefinitionOptimizeDto;
import io.camunda.optimize.dto.optimize.query.PageResultDto;
import io.camunda.optimize.service.db.reader.ProcessDefinitionReader;
import io.camunda.optimize.service.db.reader.ProcessInstanceReader;
import io.camunda.optimize.service.db.writer.ProcessInstanceWriter;
import io.camunda.optimize.service.db.writer.variable.ProcessVariableUpdateWriter;
import io.camunda.optimize.service.db.writer.variable.VariableUpdateInstanceWriter;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import io.camunda.optimize.service.util.configuration.ConfigurationServiceBuilder;
import io.camunda.optimize.service.util.configuration.cleanup.CleanupConfiguration;
import io.camunda.optimize.service.util.configuration.cleanup.CleanupMode;
import io.camunda.optimize.service.util.configuration.cleanup.ProcessDefinitionCleanupConfiguration;
import io.github.netmikey.logunit.api.LogCapturer;
import java.time.OffsetDateTime;
import java.time.Period;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.apache.commons.collections4.ListUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class OptimizeProcessCleanupServiceTest {

  private static final List<String> INSTANCE_IDS = ImmutableList.of("1", "2");
  private static final PageResultDto<String> FIRST_PAGE =
      new PageResultDto<>("1", 1, INSTANCE_IDS.subList(0, 1));
  private static final PageResultDto<String> SECOND_PAGE =
      new PageResultDto<>("1", 1, INSTANCE_IDS.subList(1, 2));

  @RegisterExtension
  LogCapturer logCapturer = LogCapturer.create().captureForType(CleanupService.class);

  @Mock private ProcessDefinitionReader processDefinitionReader;
  @Mock private ProcessInstanceReader processInstanceReader;
  @Mock private ProcessInstanceWriter processInstanceWriter;
  @Mock private ProcessVariableUpdateWriter processVariableUpdateWriter;
  @Mock private VariableUpdateInstanceWriter variableUpdateInstanceWriter;
  private ConfigurationService configurationService;

  @BeforeEach
  public void init() {
    configurationService = ConfigurationServiceBuilder.createDefaultConfiguration();
  }

  @Test
  public void testCleanupRunForMultipleProcessDefinitionsDefaultConfig() {
    // given
    final List<String> processDefinitionKeys = generateRandomDefinitionsKeys(3);
    mockProcessDefinitions(processDefinitionKeys);
    mockGetProcessInstanceIdsForProcessInstanceDelete(processDefinitionKeys);
    mockNextPageOfEntities();

    // when
    final CleanupService underTest = createOptimizeCleanupServiceToTest();
    doCleanup(underTest);

    // then
    assertDeleteProcessInstancesExecutedFor(
        processDefinitionKeys, getCleanupConfiguration().getTtl());
  }

  @Test
  public void testCleanupRunForMultipleProcessDefinitionsDifferentDefaultMode() {
    // given
    final CleanupMode customMode = CleanupMode.VARIABLES;
    getCleanupConfiguration().getProcessDataCleanupConfiguration().setCleanupMode(customMode);
    final List<String> processDefinitionKeys = generateRandomDefinitionsKeys(3);

    // when
    mockProcessDefinitions(processDefinitionKeys);
    mockGetProcessInstanceIdsForVariableDelete(processDefinitionKeys);
    mockNextPageOfEntitiesThatHaveVariables();
    final CleanupService underTest = createOptimizeCleanupServiceToTest();
    doCleanup(underTest);

    // then
    assertDeleteAllInstanceVariablesExecutedFor(
        processDefinitionKeys, getCleanupConfiguration().getTtl());
  }

  @Test
  public void testCleanupRunForMultipleProcessDefinitionsDifferentDefaultTtl() {
    // given
    final Period customTtl = Period.parse("P2M");
    getCleanupConfiguration().setTtl(customTtl);
    final List<String> processDefinitionKeys = generateRandomDefinitionsKeys(3);
    mockProcessDefinitions(processDefinitionKeys);
    mockGetProcessInstanceIdsForProcessInstanceDelete(processDefinitionKeys);
    mockNextPageOfEntities();

    // when
    final CleanupService underTest = createOptimizeCleanupServiceToTest();
    doCleanup(underTest);

    // then
    assertDeleteProcessInstancesExecutedFor(processDefinitionKeys, customTtl);
  }

  @Test
  public void testCleanupRunForMultipleProcessDefinitionsSpecificModeOverridesDefault() {
    // given
    final CleanupMode customMode = CleanupMode.VARIABLES;
    final List<String> processDefinitionKeysWithSpecificMode = generateRandomDefinitionsKeys(3);
    final Map<String, ProcessDefinitionCleanupConfiguration>
        processDefinitionSpecificConfiguration =
            getCleanupConfiguration()
                .getProcessDataCleanupConfiguration()
                .getProcessDefinitionSpecificConfiguration();
    processDefinitionKeysWithSpecificMode.forEach(
        processDefinitionKey ->
            processDefinitionSpecificConfiguration.put(
                processDefinitionKey, new ProcessDefinitionCleanupConfiguration(customMode)));
    final List<String> processDefinitionKeysWithDefaultMode = generateRandomDefinitionsKeys(3);
    final List allProcessDefinitionKeys =
        ListUtils.union(
            processDefinitionKeysWithSpecificMode, processDefinitionKeysWithDefaultMode);

    // when
    mockProcessDefinitions(allProcessDefinitionKeys);
    mockGetProcessInstanceIdsForProcessInstanceDelete(processDefinitionKeysWithDefaultMode);
    mockGetProcessInstanceIdsForVariableDelete(processDefinitionKeysWithSpecificMode);
    mockNextPageOfEntities();
    mockNextPageOfEntitiesThatHaveVariables();
    final CleanupService underTest = createOptimizeCleanupServiceToTest();
    doCleanup(underTest);

    // then
    verifyDeleteProcessInstanceExecutionReturnCapturedArguments(
        processDefinitionKeysWithDefaultMode);
    verifyDeleteAllInstanceVariablesReturnCapturedArguments(processDefinitionKeysWithSpecificMode);
  }

  @Test
  public void testCleanupRunForMultipleProcessDefinitionsSpecificTtlsOverrideDefault() {
    // given

    final Period customTtl = Period.parse("P2M");
    final List<String> processDefinitionKeysWithSpecificTtl = generateRandomDefinitionsKeys(3);
    final Map<String, ProcessDefinitionCleanupConfiguration>
        processDefinitionSpecificConfiguration =
            getCleanupConfiguration()
                .getProcessDataCleanupConfiguration()
                .getProcessDefinitionSpecificConfiguration();
    processDefinitionKeysWithSpecificTtl.forEach(
        processDefinitionKey ->
            processDefinitionSpecificConfiguration.put(
                processDefinitionKey, new ProcessDefinitionCleanupConfiguration(customTtl)));
    final List<String> processDefinitionKeysWithDefaultTtl = generateRandomDefinitionsKeys(3);
    final List<String> allProcessDefinitionKeys =
        (List<String>)
            ListUtils.union(
                processDefinitionKeysWithSpecificTtl, processDefinitionKeysWithDefaultTtl);

    // when
    mockProcessDefinitions(allProcessDefinitionKeys);
    mockGetProcessInstanceIdsForProcessInstanceDelete(allProcessDefinitionKeys);
    mockNextPageOfEntities();
    final CleanupService underTest = createOptimizeCleanupServiceToTest();
    doCleanup(underTest);

    // then
    final Map<String, OffsetDateTime> capturedArguments =
        verifyDeleteProcessInstanceExecutionReturnCapturedArguments(allProcessDefinitionKeys);
    assertInstancesWereRetrievedByKeyAndExpectedTtl(
        capturedArguments, processDefinitionKeysWithSpecificTtl, customTtl);
    assertInstancesWereRetrievedByKeyAndExpectedTtl(
        capturedArguments, processDefinitionKeysWithDefaultTtl, getCleanupConfiguration().getTtl());
  }

  @Test
  public void testCleanupRunOnceForEveryProcessDefinitionKey() {
    // given
    final List<String> processDefinitionKeys = generateRandomDefinitionsKeys(3);
    // mock returns keys twice (in reality they have different versions but that doesn't matter for
    // the test)
    mockProcessDefinitions(ListUtils.union(processDefinitionKeys, processDefinitionKeys));
    mockGetProcessInstanceIdsForProcessInstanceDelete(processDefinitionKeys);
    mockNextPageOfEntities();

    // when
    final CleanupService underTest = createOptimizeCleanupServiceToTest();
    doCleanup(underTest);

    // then
    assertDeleteProcessInstancesExecutedFor(
        processDefinitionKeys, getCleanupConfiguration().getTtl());
  }

  @Test
  public void testWarnOnCleanupOnSpecificKeyConfigWithNoMatchingProcessDefinition() {
    // given I have a key specific config
    final String misconfiguredKey = "myMistypedKey";
    getCleanupConfiguration()
        .getProcessDataCleanupConfiguration()
        .getProcessDefinitionSpecificConfiguration()
        .put(misconfiguredKey, new ProcessDefinitionCleanupConfiguration(CleanupMode.VARIABLES));
    final List<String> processDefinitionKeys = generateRandomDefinitionsKeys(3);
    mockProcessDefinitions(processDefinitionKeys);
    mockGetProcessInstanceIdsForProcessInstanceDelete(processDefinitionKeys);
    mockNextPageOfEntities();

    // when
    final CleanupService underTest = createOptimizeCleanupServiceToTest();
    doCleanup(underTest);

    // then
    assertDeleteProcessInstancesExecutedFor(
        processDefinitionKeys, getCleanupConfiguration().getTtl());

    // and it warns on misconfigured keys
    logCapturer.assertContains(
        String.format(
            "History Cleanup Configuration contains definition keys for which there is no "
                + "definition imported yet. The keys without a match in the database are: [%s]",
            misconfiguredKey));
  }

  private void mockGetProcessInstanceIdsForProcessInstanceDelete(final List<String> expectedKeys) {
    expectedKeys.forEach(
        key -> {
          when(processInstanceReader.getFirstPageOfProcessInstanceIdsThatEndedBefore(
                  eq(key), ArgumentMatchers.any(OffsetDateTime.class), anyInt()))
              .thenReturn(FIRST_PAGE);
        });
  }

  private void mockNextPageOfEntities() {
    when(processInstanceReader.getNextPageOfProcessInstanceIdsThatEndedBefore(
            anyString(), any(OffsetDateTime.class), anyInt(), eq(FIRST_PAGE)))
        .thenReturn(SECOND_PAGE);
    when(processInstanceReader.getNextPageOfProcessInstanceIdsThatEndedBefore(
            anyString(), any(OffsetDateTime.class), anyInt(), eq(SECOND_PAGE)))
        .thenReturn(new PageResultDto<>(1));
  }

  private void mockNextPageOfEntitiesThatHaveVariables() {
    when(processInstanceReader.getNextPageOfProcessInstanceIdsThatHaveVariablesAndEndedBefore(
            anyString(), any(OffsetDateTime.class), anyInt(), eq(FIRST_PAGE)))
        .thenReturn(SECOND_PAGE);
    when(processInstanceReader.getNextPageOfProcessInstanceIdsThatHaveVariablesAndEndedBefore(
            anyString(), any(OffsetDateTime.class), anyInt(), eq(SECOND_PAGE)))
        .thenReturn(new PageResultDto<>(1));
  }

  private void mockGetProcessInstanceIdsForVariableDelete(final List<String> expectedKeys) {
    expectedKeys.forEach(
        key -> {
          when(processInstanceReader
                  .getFirstPageOfProcessInstanceIdsThatHaveVariablesAndEndedBefore(
                      eq(key), ArgumentMatchers.any(OffsetDateTime.class), anyInt()))
              .thenReturn(FIRST_PAGE);
        });
  }

  private void doCleanup(final CleanupService underTest) {
    underTest.doCleanup(OffsetDateTime.now());
  }

  private CleanupConfiguration getCleanupConfiguration() {
    return configurationService.getCleanupServiceConfiguration();
  }

  private void assertDeleteProcessInstancesExecutedFor(
      final List<String> expectedProcessDefinitionKeys, final Period expectedTtl) {
    final Map<String, OffsetDateTime> processInstanceKeysWithDateFilter =
        verifyDeleteProcessInstanceExecutionReturnCapturedArguments(expectedProcessDefinitionKeys);
    assertInstancesWereRetrievedByKeyAndExpectedTtl(
        processInstanceKeysWithDateFilter, expectedProcessDefinitionKeys, expectedTtl);

    verify(processInstanceWriter, times(expectedProcessDefinitionKeys.size()))
        .deleteByIds(any(), eq(FIRST_PAGE.getEntities()));
    verify(processInstanceWriter, times(expectedProcessDefinitionKeys.size()))
        .deleteByIds(any(), eq(SECOND_PAGE.getEntities()));
    verify(variableUpdateInstanceWriter, times(expectedProcessDefinitionKeys.size()))
        .deleteByProcessInstanceIds(eq(FIRST_PAGE.getEntities()));
    verify(variableUpdateInstanceWriter, times(expectedProcessDefinitionKeys.size()))
        .deleteByProcessInstanceIds(eq(SECOND_PAGE.getEntities()));
  }

  private void assertInstancesWereRetrievedByKeyAndExpectedTtl(
      final Map<String, OffsetDateTime> capturedInvocationArguments,
      final List<String> expectedDefinitionKeys,
      final Period expectedTtl) {
    final Map<String, OffsetDateTime> filteredInvocationArguments =
        capturedInvocationArguments.entrySet().stream()
            .filter(entry -> expectedDefinitionKeys.contains(entry.getKey()))
            .collect(toMap(Map.Entry::getKey, Map.Entry::getValue));
    assertThat(filteredInvocationArguments).hasSize(expectedDefinitionKeys.size());

    final OffsetDateTime dateFilterValue =
        filteredInvocationArguments.values().toArray(new OffsetDateTime[] {})[0];
    assertThat(dateFilterValue).isBeforeOrEqualTo(OffsetDateTime.now().minus(expectedTtl));
    filteredInvocationArguments
        .values()
        .forEach(instant -> assertThat(instant).isEqualTo(dateFilterValue));
  }

  private Map<String, OffsetDateTime> verifyDeleteProcessInstanceExecutionReturnCapturedArguments(
      final List<String> expectedProcessDefinitionKeys) {
    final ArgumentCaptor<String> definitionKeyCaptor = ArgumentCaptor.forClass(String.class);
    final ArgumentCaptor<OffsetDateTime> endDateFilterCaptor =
        ArgumentCaptor.forClass(OffsetDateTime.class);
    verify(processInstanceReader, atLeast(expectedProcessDefinitionKeys.size()))
        .getFirstPageOfProcessInstanceIdsThatEndedBefore(
            definitionKeyCaptor.capture(), endDateFilterCaptor.capture(), anyInt());
    int i = 0;
    final Map<String, OffsetDateTime> definitionKeysWithDateFilter = new HashMap<>();
    for (final String key : definitionKeyCaptor.getAllValues()) {
      definitionKeysWithDateFilter.put(key, endDateFilterCaptor.getAllValues().get(i));
      i++;
    }
    return definitionKeysWithDateFilter;
  }

  private void assertDeleteAllInstanceVariablesExecutedFor(
      final List<String> expectedProcessDefinitionKeys, final Period expectedTtl) {
    final Map<String, OffsetDateTime> processInstanceKeysWithDateFilter =
        verifyDeleteAllInstanceVariablesReturnCapturedArguments(expectedProcessDefinitionKeys);

    assertInstancesWereRetrievedByKeyAndExpectedTtl(
        processInstanceKeysWithDateFilter, expectedProcessDefinitionKeys, expectedTtl);
  }

  private Map<String, OffsetDateTime> verifyDeleteAllInstanceVariablesReturnCapturedArguments(
      final List<String> expectedProcessDefinitionKeys) {
    final ArgumentCaptor<String> processInstanceCaptor = ArgumentCaptor.forClass(String.class);
    final ArgumentCaptor<OffsetDateTime> endDateFilterCaptor =
        ArgumentCaptor.forClass(OffsetDateTime.class);
    verify(processInstanceReader, atLeast(expectedProcessDefinitionKeys.size()))
        .getFirstPageOfProcessInstanceIdsThatHaveVariablesAndEndedBefore(
            processInstanceCaptor.capture(), endDateFilterCaptor.capture(), anyInt());
    int i = 0;
    final Map<String, OffsetDateTime> filteredProcessInstancesWithDateFilter = new HashMap<>();
    for (final String key : processInstanceCaptor.getAllValues()) {
      filteredProcessInstancesWithDateFilter.put(key, endDateFilterCaptor.getAllValues().get(i));
      i++;
    }
    return filteredProcessInstancesWithDateFilter;
  }

  private void mockProcessDefinitions(final List<String> processDefinitionIds) {
    final List<ProcessDefinitionOptimizeDto> processDefinitionOptimizeDtos =
        processDefinitionIds.stream()
            .map(this::createProcessDefinitionDto)
            .collect(Collectors.toList());
    when(processDefinitionReader.getAllProcessDefinitions())
        .thenReturn(processDefinitionOptimizeDtos);
  }

  private List<String> generateRandomDefinitionsKeys(final Integer amount) {
    return IntStream.range(0, amount).mapToObj(i -> UUID.randomUUID().toString()).toList();
  }

  private ProcessDefinitionOptimizeDto createProcessDefinitionDto(final String key) {
    final ProcessDefinitionOptimizeDto processDefinitionOptimizeDto =
        ProcessDefinitionOptimizeDto.builder().key(key).build();
    return processDefinitionOptimizeDto;
  }

  private CleanupService createOptimizeCleanupServiceToTest() {
    return new EngineDataProcessCleanupService(
        configurationService,
        processDefinitionReader,
        processInstanceReader,
        processInstanceWriter,
        processVariableUpdateWriter,
        variableUpdateInstanceWriter);
  }
}
