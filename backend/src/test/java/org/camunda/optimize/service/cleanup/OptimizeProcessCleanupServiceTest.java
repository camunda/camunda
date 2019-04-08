/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.cleanup;

import org.apache.commons.collections.ListUtils;
import org.camunda.optimize.dto.optimize.importing.DecisionDefinitionOptimizeDto;
import org.camunda.optimize.dto.optimize.importing.ProcessDefinitionOptimizeDto;
import org.camunda.optimize.service.es.reader.ProcessDefinitionReader;
import org.camunda.optimize.service.es.writer.CompletedProcessInstanceWriter;
import org.camunda.optimize.service.es.writer.variable.VariableWriter;
import org.camunda.optimize.service.exceptions.OptimizeConfigurationException;
import org.camunda.optimize.service.util.configuration.CleanupMode;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.camunda.optimize.service.util.configuration.OptimizeCleanupConfiguration;
import org.camunda.optimize.service.util.configuration.ProcessDefinitionCleanupConfiguration;
import org.hamcrest.CoreMatchers;
import org.hamcrest.MatcherAssert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.time.OffsetDateTime;
import java.time.Period;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class OptimizeProcessCleanupServiceTest {

  @Mock
  private ProcessDefinitionReader processDefinitionReader;
  @Mock
  private CompletedProcessInstanceWriter processInstanceWriter;
  @Mock
  private VariableWriter variableWriter;

  private ConfigurationService configurationService;

  @Before
  public void init() {
    configurationService = new ConfigurationService();
    mockProcessDefinitions(new ArrayList<>());
  }

  @Test
  public void testCleanupRunForMultipleProcessDefinitionsDefaultConfig() {
    // given
    final List<String> processDefinitionKeys = generateRandomDefinitionsKeys(3);
    mockProcessDefinitions(processDefinitionKeys);

    //when
    final OptimizeCleanupService underTest = createOptimizeCleanupServiceToTest();
    doCleanup(underTest);

    //then
    assertDeleteProcessInstancesExecutedFor(processDefinitionKeys, getCleanupConfig().getDefaultTtl());
  }

  @Test
  public void testCleanupRunForMultipleProcessDefinitionsDifferentDefaultMode() {
    // given
    final CleanupMode customMode = CleanupMode.VARIABLES;
    getCleanupConfig().setDefaultProcessDataCleanupMode(customMode);
    final List<String> processDefinitionKeys = generateRandomDefinitionsKeys(3);

    //when
    mockProcessDefinitions(processDefinitionKeys);
    final OptimizeCleanupService underTest = createOptimizeCleanupServiceToTest();
    doCleanup(underTest);

    //then
    assertDeleteAllInstanceVariablesExecutedFor(processDefinitionKeys, getCleanupConfig().getDefaultTtl());
  }

  @Test
  public void testCleanupRunForMultipleProcessDefinitionsDifferentDefaultTtl() {
    // given
    final Period customTtl = Period.parse("P2M");
    getCleanupConfig().setDefaultTtl(customTtl);
    final List<String> processDefinitionKeys = generateRandomDefinitionsKeys(3);

    //when
    mockProcessDefinitions(processDefinitionKeys);
    final OptimizeCleanupService underTest = createOptimizeCleanupServiceToTest();
    doCleanup(underTest);

    //then
    assertDeleteProcessInstancesExecutedFor(processDefinitionKeys, customTtl);
  }

  @Test
  public void testCleanupRunForMultipleProcessDefinitionsSpecificModeOverridesDefault() {
    // given
    final CleanupMode customMode = CleanupMode.VARIABLES;
    final List<String> processDefinitionKeysWithSpecificMode = generateRandomDefinitionsKeys(3);
    Map<String, ProcessDefinitionCleanupConfiguration> processDefinitionSpecificConfiguration =
      getCleanupConfig().getProcessDefinitionSpecificConfiguration();
    processDefinitionKeysWithSpecificMode.forEach(processDefinitionKey -> processDefinitionSpecificConfiguration.put(
      processDefinitionKey, new ProcessDefinitionCleanupConfiguration(customMode)
    ));
    final List<String> processDefinitionKeysWithDefaultMode = generateRandomDefinitionsKeys(3);
    final List allProcessDefinitionKeys = ListUtils.union(
      processDefinitionKeysWithSpecificMode,
      processDefinitionKeysWithDefaultMode
    );

    //when
    mockProcessDefinitions(allProcessDefinitionKeys);
    final OptimizeCleanupService underTest = createOptimizeCleanupServiceToTest();
    doCleanup(underTest);

    //then
    verifyDeleteProcessInstanceExecutionReturnCapturedArguments(processDefinitionKeysWithDefaultMode);
    verifyDeleteAllInstanceVariablesReturnCapturedArguments(processDefinitionKeysWithSpecificMode);
  }

  @Test
  public void testCleanupRunForMultipleProcessDefinitionsSpecificTtlsOverrideDefault() {
    // given
    final Period customTtl = Period.parse("P2M");
    final List<String> processDefinitionKeysWithSpecificTtl = generateRandomDefinitionsKeys(3);
    Map<String, ProcessDefinitionCleanupConfiguration> processDefinitionSpecificConfiguration =
      getCleanupConfig().getProcessDefinitionSpecificConfiguration();
    processDefinitionKeysWithSpecificTtl.forEach(processDefinitionKey -> processDefinitionSpecificConfiguration.put(
      processDefinitionKey, new ProcessDefinitionCleanupConfiguration(customTtl)
    ));
    final List<String> processDefinitionKeysWithDefaultTtl = generateRandomDefinitionsKeys(3);
    final List allProcessDefinitionKeys = ListUtils.union(
      processDefinitionKeysWithSpecificTtl,
      processDefinitionKeysWithDefaultTtl
    );

    //when
    mockProcessDefinitions(allProcessDefinitionKeys);
    final OptimizeCleanupService underTest = createOptimizeCleanupServiceToTest();
    doCleanup(underTest);

    //then
    Map<String, OffsetDateTime> capturedArguments = verifyDeleteProcessInstanceExecutionReturnCapturedArguments(
      allProcessDefinitionKeys
    );
    assertKeysWereCalledWithExpectedTtl(capturedArguments, processDefinitionKeysWithSpecificTtl, customTtl);
    assertKeysWereCalledWithExpectedTtl(
      capturedArguments, processDefinitionKeysWithDefaultTtl, getCleanupConfig().getDefaultTtl()
    );
  }

  @Test
  public void testCleanupRunOnceForEveryProcessDefinitionKey() {
    // given
    final List<String> processDefinitionKeys = generateRandomDefinitionsKeys(3);
    // mock returns keys twice (in reality they have different versions but that doesn't matter for the test)
    mockProcessDefinitions(ListUtils.union(processDefinitionKeys, processDefinitionKeys));

    //when
    final OptimizeCleanupService underTest = createOptimizeCleanupServiceToTest();
    doCleanup(underTest);

    //then
    assertDeleteProcessInstancesExecutedFor(processDefinitionKeys, getCleanupConfig().getDefaultTtl());
  }

  @Test
  public void testFailCleanupOnSpecificKeyConfigWithNoMatchingProcessDefinition() {
    // given I have a key specific config
    final String configuredKey = "myMistypedKey";
    getCleanupConfig().getProcessDefinitionSpecificConfiguration().put(
      configuredKey,
      new ProcessDefinitionCleanupConfiguration(CleanupMode.VARIABLES)
    );
    // and this key is not present in the known process definition keys
    mockProcessDefinitions(generateRandomDefinitionsKeys(3));

    //when I run the cleanup
    final OptimizeCleanupService underTest = createOptimizeCleanupServiceToTest();
    OptimizeConfigurationException expectedException = null;
    try {
      doCleanup(underTest);
    } catch (OptimizeConfigurationException e) {
      expectedException = e;
    }

    //then it fails with an exception
    MatcherAssert.assertThat(expectedException, CoreMatchers.is((notNullValue())));
    MatcherAssert.assertThat(expectedException.getMessage(), containsString(configuredKey));
  }

  private void doCleanup(final OptimizeCleanupService underTest) {
    underTest.doCleanup(OffsetDateTime.now());
  }

  private OptimizeCleanupConfiguration getCleanupConfig() {
    return configurationService.getCleanupServiceConfiguration();
  }

  private void assertDeleteProcessInstancesExecutedFor(List<String> expectedProcessDefinitionKeys, Period expectedTtl) {
    final Map<String, OffsetDateTime> processInstanceKeysWithDateFilter =
      verifyDeleteProcessInstanceExecutionReturnCapturedArguments(expectedProcessDefinitionKeys);

    assertKeysWereCalledWithExpectedTtl(processInstanceKeysWithDateFilter, expectedProcessDefinitionKeys, expectedTtl);
  }

  private void assertKeysWereCalledWithExpectedTtl(Map<String, OffsetDateTime> capturedInvocationArguments,
                                                   List<String> expectedDefinitionKeys,
                                                   Period expectedTtl) {
    final Map<String, OffsetDateTime> filteredInvocationArguments = capturedInvocationArguments.entrySet().stream()
      .filter(entry -> expectedDefinitionKeys.contains(entry.getKey()))
      .collect(toMap(Map.Entry::getKey, Map.Entry::getValue));
    assertThat(filteredInvocationArguments.size(), is(expectedDefinitionKeys.size()));

    final OffsetDateTime dateFilterValue = filteredInvocationArguments.values().toArray(new OffsetDateTime[]{})[0];
    assertThat(dateFilterValue, lessThanOrEqualTo(OffsetDateTime.now().minus(expectedTtl)));
    filteredInvocationArguments.values().forEach(instant -> assertThat(instant, is(dateFilterValue)));
  }

  private Map<String, OffsetDateTime> verifyDeleteProcessInstanceExecutionReturnCapturedArguments(List<String> expectedProcessDefinitionKeys) {
    ArgumentCaptor<String> processInstanceCaptor = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<OffsetDateTime> endDateFilterCaptor = ArgumentCaptor.forClass(OffsetDateTime.class);
    verify(
      processInstanceWriter,
      atLeast(expectedProcessDefinitionKeys.size())
    ).deleteProcessInstancesByProcessDefinitionKeyAndEndDateOlderThan(
      processInstanceCaptor.capture(),
      endDateFilterCaptor.capture()
    );
    int i = 0;
    final Map<String, OffsetDateTime> filteredProcessInstancesWithDateFilter = new HashMap<>();
    for (String key : processInstanceCaptor.getAllValues()) {
      filteredProcessInstancesWithDateFilter.put(key, endDateFilterCaptor.getAllValues().get(i));
      i++;
    }
    return filteredProcessInstancesWithDateFilter;
  }

  private void assertDeleteAllInstanceVariablesExecutedFor(List<String> expectedProcessDefinitionKeys,
                                                           Period expectedTtl) {
    final Map<String, OffsetDateTime> processInstanceKeysWithDateFilter =
      verifyDeleteAllInstanceVariablesReturnCapturedArguments(expectedProcessDefinitionKeys);

    assertKeysWereCalledWithExpectedTtl(processInstanceKeysWithDateFilter, expectedProcessDefinitionKeys, expectedTtl);
  }

  private Map<String, OffsetDateTime> verifyDeleteAllInstanceVariablesReturnCapturedArguments(List<String> expectedProcessDefinitionKeys) {
    ArgumentCaptor<String> processInstanceCaptor = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<OffsetDateTime> endDateFilterCaptor = ArgumentCaptor.forClass(OffsetDateTime.class);
    verify(
      variableWriter,
      atLeast(expectedProcessDefinitionKeys.size())
    ).deleteAllInstanceVariablesByProcessDefinitionKeyAndEndDateOlderThan(
      processInstanceCaptor.capture(),
      endDateFilterCaptor.capture()
    );
    int i = 0;
    final Map<String, OffsetDateTime> filteredProcessInstancesWithDateFilter = new HashMap<>();
    for (String key : processInstanceCaptor.getAllValues()) {
      filteredProcessInstancesWithDateFilter.put(key, endDateFilterCaptor.getAllValues().get(i));
      i++;
    }
    return filteredProcessInstancesWithDateFilter;
  }

  private List<String> mockProcessDefinitions(List<String> processDefinitionIds) {
    final List<ProcessDefinitionOptimizeDto> processDefinitionOptimizeDtos = processDefinitionIds.stream()
      .map(this::createProcessDefinitionDto)
      .collect(Collectors.toList());
    when(processDefinitionReader.fetchFullyImportedProcessDefinitionsAsService()).thenReturn(processDefinitionOptimizeDtos);
    return processDefinitionIds;
  }

  private List<String> generateRandomDefinitionsKeys(Integer amount) {
    return IntStream.range(0, amount)
      .mapToObj(i -> UUID.randomUUID().toString())
      .collect(toList());
  }

  private ProcessDefinitionOptimizeDto createProcessDefinitionDto(String key) {
    ProcessDefinitionOptimizeDto processDefinitionOptimizeDto = new ProcessDefinitionOptimizeDto();
    processDefinitionOptimizeDto.setKey(key);
    return processDefinitionOptimizeDto;
  }

  private DecisionDefinitionOptimizeDto createDecisionDefinitionDto(String key) {
    DecisionDefinitionOptimizeDto decisionDefinitionOptimizeDto = new DecisionDefinitionOptimizeDto();
    decisionDefinitionOptimizeDto.setKey(key);
    return decisionDefinitionOptimizeDto;
  }

  private OptimizeCleanupService createOptimizeCleanupServiceToTest() {
    return new OptimizeProcessCleanupService(
      configurationService, processDefinitionReader, processInstanceWriter, variableWriter
    );
  }
}
