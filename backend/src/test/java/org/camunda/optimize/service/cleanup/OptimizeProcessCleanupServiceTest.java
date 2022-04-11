/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.cleanup;

import com.google.common.collect.ImmutableList;
import org.apache.commons.collections.ListUtils;
import org.camunda.optimize.dto.optimize.ProcessDefinitionOptimizeDto;
import org.camunda.optimize.dto.optimize.query.PageResultDto;
import org.camunda.optimize.service.es.reader.ProcessDefinitionReader;
import org.camunda.optimize.service.es.reader.ProcessInstanceReader;
import org.camunda.optimize.service.es.writer.BusinessKeyWriter;
import org.camunda.optimize.service.es.writer.CamundaActivityEventWriter;
import org.camunda.optimize.service.es.writer.CompletedProcessInstanceWriter;
import org.camunda.optimize.service.es.writer.variable.ProcessVariableUpdateWriter;
import org.camunda.optimize.service.es.writer.variable.VariableUpdateInstanceWriter;
import org.camunda.optimize.service.exceptions.OptimizeConfigurationException;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.camunda.optimize.service.util.configuration.ConfigurationServiceBuilder;
import org.camunda.optimize.service.util.configuration.cleanup.CleanupConfiguration;
import org.camunda.optimize.service.util.configuration.cleanup.CleanupMode;
import org.camunda.optimize.service.util.configuration.cleanup.ProcessDefinitionCleanupConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.time.Period;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class OptimizeProcessCleanupServiceTest {
  private static final List<String> INSTANCE_IDS = ImmutableList.of("1", "2");
  private static final PageResultDto<String> FIRST_PAGE = new PageResultDto<>("1", 1, INSTANCE_IDS.subList(0, 1));
  private static final PageResultDto<String> SECOND_PAGE = new PageResultDto<>("1", 1, INSTANCE_IDS.subList(1, 2));

  @Mock
  private ProcessDefinitionReader processDefinitionReader;
  @Mock
  private ProcessInstanceReader processInstanceReader;
  @Mock
  private CompletedProcessInstanceWriter processInstanceWriter;
  @Mock
  private ProcessVariableUpdateWriter processVariableUpdateWriter;
  @Mock
  private VariableUpdateInstanceWriter variableUpdateInstanceWriter;
  @Mock
  private BusinessKeyWriter businessKeyWriter;
  @Mock
  private CamundaActivityEventWriter camundaActivityEventWriter;

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
    assertDeleteProcessInstancesExecutedFor(processDefinitionKeys, getCleanupConfiguration().getTtl());
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
    assertDeleteAllInstanceVariablesExecutedFor(processDefinitionKeys, getCleanupConfiguration().getTtl());
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
    Map<String, ProcessDefinitionCleanupConfiguration> processDefinitionSpecificConfiguration =
      getCleanupConfiguration().getProcessDataCleanupConfiguration().getProcessDefinitionSpecificConfiguration();
    processDefinitionKeysWithSpecificMode.forEach(processDefinitionKey -> processDefinitionSpecificConfiguration.put(
      processDefinitionKey, new ProcessDefinitionCleanupConfiguration(customMode)
    ));
    final List<String> processDefinitionKeysWithDefaultMode = generateRandomDefinitionsKeys(3);
    final List allProcessDefinitionKeys = ListUtils.union(
      processDefinitionKeysWithSpecificMode,
      processDefinitionKeysWithDefaultMode
    );

    // when
    mockProcessDefinitions(allProcessDefinitionKeys);
    mockGetProcessInstanceIdsForProcessInstanceDelete(processDefinitionKeysWithDefaultMode);
    mockGetProcessInstanceIdsForVariableDelete(processDefinitionKeysWithSpecificMode);
    mockNextPageOfEntities();
    mockNextPageOfEntitiesThatHaveVariables();
    final CleanupService underTest = createOptimizeCleanupServiceToTest();
    doCleanup(underTest);

    // then
    verifyDeleteProcessInstanceExecutionReturnCapturedArguments(processDefinitionKeysWithDefaultMode);
    verifyDeleteAllInstanceVariablesReturnCapturedArguments(processDefinitionKeysWithSpecificMode);
  }

  @Test
  public void testCleanupRunForMultipleProcessDefinitionsSpecificTtlsOverrideDefault() {
    // given

    final Period customTtl = Period.parse("P2M");
    final List<String> processDefinitionKeysWithSpecificTtl = generateRandomDefinitionsKeys(3);
    Map<String, ProcessDefinitionCleanupConfiguration> processDefinitionSpecificConfiguration =
      getCleanupConfiguration().getProcessDataCleanupConfiguration().getProcessDefinitionSpecificConfiguration();
    processDefinitionKeysWithSpecificTtl.forEach(processDefinitionKey -> processDefinitionSpecificConfiguration.put(
      processDefinitionKey, new ProcessDefinitionCleanupConfiguration(customTtl)
    ));
    final List<String> processDefinitionKeysWithDefaultTtl = generateRandomDefinitionsKeys(3);
    final List<String> allProcessDefinitionKeys = (List<String>) ListUtils.union(
      processDefinitionKeysWithSpecificTtl,
      processDefinitionKeysWithDefaultTtl
    );

    // when
    mockProcessDefinitions(allProcessDefinitionKeys);
    mockGetProcessInstanceIdsForProcessInstanceDelete(allProcessDefinitionKeys);
//    mockGetProcessInstanceDefinitionKeysForProcessInstanceDelete(allProcessDefinitionKeys);
    mockNextPageOfEntities();
    final CleanupService underTest = createOptimizeCleanupServiceToTest();
    doCleanup(underTest);

    // then
    Map<String, OffsetDateTime> capturedArguments = verifyDeleteProcessInstanceExecutionReturnCapturedArguments(
      allProcessDefinitionKeys
    );
    assertInstancesWereRetrievedByKeyAndExpectedTtl(capturedArguments, processDefinitionKeysWithSpecificTtl, customTtl);
    assertInstancesWereRetrievedByKeyAndExpectedTtl(
      capturedArguments, processDefinitionKeysWithDefaultTtl, getCleanupConfiguration().getTtl()
    );
  }

  @Test
  public void testCleanupRunOnceForEveryProcessDefinitionKey() {
    // given
    final List<String> processDefinitionKeys = generateRandomDefinitionsKeys(3);
    // mock returns keys twice (in reality they have different versions but that doesn't matter for the test)
    mockProcessDefinitions(ListUtils.union(processDefinitionKeys, processDefinitionKeys));
    mockGetProcessInstanceIdsForProcessInstanceDelete(processDefinitionKeys);
    mockNextPageOfEntities();

    // when
    final CleanupService underTest = createOptimizeCleanupServiceToTest();
    doCleanup(underTest);

    // then
    assertDeleteProcessInstancesExecutedFor(processDefinitionKeys, getCleanupConfiguration().getTtl());
  }

  @Test
  public void testFailCleanupOnSpecificKeyConfigWithNoMatchingProcessDefinition() {
    // given I have a key specific config
    final String configuredKey = "myMistypedKey";
    getCleanupConfiguration()
      .getProcessDataCleanupConfiguration()
      .getProcessDefinitionSpecificConfiguration()
      .put(
        configuredKey,
        new ProcessDefinitionCleanupConfiguration(CleanupMode.VARIABLES)
      );
    // and this key is not present in the known process definition keys
    mockProcessDefinitions(generateRandomDefinitionsKeys(3));

    // when I run the cleanup
    final CleanupService underTest = createOptimizeCleanupServiceToTest();

    // then it fails with an exception
    OptimizeConfigurationException exception = assertThrows(
      OptimizeConfigurationException.class,
      () -> doCleanup(underTest)
    );
    assertThat(exception.getMessage()).contains(configuredKey);
  }

  private void mockGetProcessInstanceIdsForProcessInstanceDelete(final List<String> expectedKeys) {
    expectedKeys.forEach(key -> {
      when(processInstanceReader.getFirstPageOfProcessInstanceIdsThatEndedBefore(
        eq(key), ArgumentMatchers.any(OffsetDateTime.class), anyInt()
      )).thenReturn(FIRST_PAGE);
    });
  }

  private void mockNextPageOfEntities() {
    when(processInstanceReader.getNextPageOfProcessInstanceIdsThatEndedBefore(
      anyString(),
      any(OffsetDateTime.class),
      anyInt(),
      eq(FIRST_PAGE)
    )).thenReturn(SECOND_PAGE);
    when(processInstanceReader.getNextPageOfProcessInstanceIdsThatEndedBefore(
      anyString(),
      any(OffsetDateTime.class),
      anyInt(),
      eq(SECOND_PAGE)
    )).thenReturn(new PageResultDto<>(1));
  }

  private void mockNextPageOfEntitiesThatHaveVariables() {
    when(processInstanceReader.getNextPageOfProcessInstanceIdsThatHaveVariablesAndEndedBefore(
      anyString(),
      any(OffsetDateTime.class),
      anyInt(),
      eq(FIRST_PAGE)
    )).thenReturn(SECOND_PAGE);
    when(processInstanceReader.getNextPageOfProcessInstanceIdsThatHaveVariablesAndEndedBefore(
      anyString(),
      any(OffsetDateTime.class),
      anyInt(),
      eq(SECOND_PAGE)
    )).thenReturn(new PageResultDto<>(1));
  }

  private void mockGetProcessInstanceIdsForVariableDelete(final List<String> expectedKeys) {
    expectedKeys.forEach(key -> {
      when(processInstanceReader.getFirstPageOfProcessInstanceIdsThatHaveVariablesAndEndedBefore(
        eq(key), ArgumentMatchers.any(OffsetDateTime.class), anyInt()
      )).thenReturn(FIRST_PAGE);
    });
  }

  private void doCleanup(final CleanupService underTest) {
    underTest.doCleanup(OffsetDateTime.now());
  }

  private CleanupConfiguration getCleanupConfiguration() {
    return configurationService.getCleanupServiceConfiguration();
  }

  private void assertDeleteProcessInstancesExecutedFor(final List<String> expectedProcessDefinitionKeys,
                                                       final Period expectedTtl) {
    final Map<String, OffsetDateTime> processInstanceKeysWithDateFilter =
      verifyDeleteProcessInstanceExecutionReturnCapturedArguments(expectedProcessDefinitionKeys);
    assertInstancesWereRetrievedByKeyAndExpectedTtl(
      processInstanceKeysWithDateFilter,
      expectedProcessDefinitionKeys,
      expectedTtl
    );

    verify(processInstanceWriter, times(expectedProcessDefinitionKeys.size()))
      .deleteByIds(any(), eq(FIRST_PAGE.getEntities()));
    verify(processInstanceWriter, times(expectedProcessDefinitionKeys.size()))
      .deleteByIds(any(), eq(SECOND_PAGE.getEntities()));
    verify(variableUpdateInstanceWriter, times(expectedProcessDefinitionKeys.size()))
      .deleteByProcessInstanceIds(eq(FIRST_PAGE.getEntities()));
    verify(variableUpdateInstanceWriter, times(expectedProcessDefinitionKeys.size()))
      .deleteByProcessInstanceIds(eq(SECOND_PAGE.getEntities()));
  }

  private void assertInstancesWereRetrievedByKeyAndExpectedTtl(final Map<String, OffsetDateTime> capturedInvocationArguments,
                                                               final List<String> expectedDefinitionKeys,
                                                               final Period expectedTtl) {
    final Map<String, OffsetDateTime> filteredInvocationArguments = capturedInvocationArguments.entrySet().stream()
      .filter(entry -> expectedDefinitionKeys.contains(entry.getKey()))
      .collect(toMap(Map.Entry::getKey, Map.Entry::getValue));
    assertThat(filteredInvocationArguments).hasSize(expectedDefinitionKeys.size());

    final OffsetDateTime dateFilterValue = filteredInvocationArguments.values().toArray(new OffsetDateTime[]{})[0];
    assertThat(dateFilterValue).isBeforeOrEqualTo(OffsetDateTime.now().minus(expectedTtl));
    filteredInvocationArguments.values().forEach(instant -> assertThat(instant).isEqualTo(dateFilterValue));
  }

  private Map<String, OffsetDateTime> verifyDeleteProcessInstanceExecutionReturnCapturedArguments(final List<String> expectedProcessDefinitionKeys) {
    ArgumentCaptor<String> definitionKeyCaptor = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<OffsetDateTime> endDateFilterCaptor = ArgumentCaptor.forClass(OffsetDateTime.class);
    verify(processInstanceReader, atLeast(expectedProcessDefinitionKeys.size()))
      .getFirstPageOfProcessInstanceIdsThatEndedBefore(
        definitionKeyCaptor.capture(),
        endDateFilterCaptor.capture(),
        anyInt()
      );
    int i = 0;
    final Map<String, OffsetDateTime> definitionKeysWithDateFilter = new HashMap<>();
    for (String key : definitionKeyCaptor.getAllValues()) {
      definitionKeysWithDateFilter.put(key, endDateFilterCaptor.getAllValues().get(i));
      i++;
    }
    return definitionKeysWithDateFilter;
  }

  private void assertDeleteAllInstanceVariablesExecutedFor(List<String> expectedProcessDefinitionKeys,
                                                           Period expectedTtl) {
    final Map<String, OffsetDateTime> processInstanceKeysWithDateFilter =
      verifyDeleteAllInstanceVariablesReturnCapturedArguments(expectedProcessDefinitionKeys);

    assertInstancesWereRetrievedByKeyAndExpectedTtl(
      processInstanceKeysWithDateFilter,
      expectedProcessDefinitionKeys,
      expectedTtl
    );
  }

  private Map<String, OffsetDateTime> verifyDeleteAllInstanceVariablesReturnCapturedArguments(final List<String> expectedProcessDefinitionKeys) {
    ArgumentCaptor<String> processInstanceCaptor = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<OffsetDateTime> endDateFilterCaptor = ArgumentCaptor.forClass(OffsetDateTime.class);
    verify(processInstanceReader, atLeast(expectedProcessDefinitionKeys.size()))
      .getFirstPageOfProcessInstanceIdsThatHaveVariablesAndEndedBefore(
        processInstanceCaptor.capture(), endDateFilterCaptor.capture(), anyInt()
      );
    int i = 0;
    final Map<String, OffsetDateTime> filteredProcessInstancesWithDateFilter = new HashMap<>();
    for (String key : processInstanceCaptor.getAllValues()) {
      filteredProcessInstancesWithDateFilter.put(key, endDateFilterCaptor.getAllValues().get(i));
      i++;
    }
    return filteredProcessInstancesWithDateFilter;
  }

  private List<String> mockProcessDefinitions(final List<String> processDefinitionIds) {
    final List<ProcessDefinitionOptimizeDto> processDefinitionOptimizeDtos = processDefinitionIds.stream()
      .map(this::createProcessDefinitionDto)
      .collect(Collectors.toList());
    when(processDefinitionReader.getProcessDefinitions(any()))
      .thenReturn(processDefinitionOptimizeDtos);
    return processDefinitionIds;
  }

  private List<String> generateRandomDefinitionsKeys(final Integer amount) {
    return IntStream.range(0, amount)
      .mapToObj(i -> UUID.randomUUID().toString())
      .collect(toList());
  }

  private ProcessDefinitionOptimizeDto createProcessDefinitionDto(String key) {
    final ProcessDefinitionOptimizeDto processDefinitionOptimizeDto = ProcessDefinitionOptimizeDto.builder()
      .key(key)
      .build();
    return processDefinitionOptimizeDto;
  }

  private CleanupService createOptimizeCleanupServiceToTest() {
    return new EngineDataProcessCleanupService(
      configurationService,
      processDefinitionReader,
      processInstanceReader,
      processInstanceWriter,
      processVariableUpdateWriter,
      businessKeyWriter,
      camundaActivityEventWriter,
      variableUpdateInstanceWriter
    );
  }
}
