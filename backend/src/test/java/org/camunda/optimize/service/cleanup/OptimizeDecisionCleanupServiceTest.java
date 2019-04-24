/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.cleanup;

import org.apache.commons.collections.ListUtils;
import org.camunda.optimize.dto.optimize.importing.DecisionDefinitionOptimizeDto;
import org.camunda.optimize.service.es.reader.DecisionDefinitionReader;
import org.camunda.optimize.service.es.writer.DecisionInstanceWriter;
import org.camunda.optimize.service.exceptions.OptimizeConfigurationException;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.camunda.optimize.service.util.configuration.DecisionDefinitionCleanupConfiguration;
import org.camunda.optimize.service.util.configuration.OptimizeCleanupConfiguration;
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
public class OptimizeDecisionCleanupServiceTest {
  @Mock
  private DecisionDefinitionReader decisionDefinitionReader;
  @Mock
  private DecisionInstanceWriter decisionInstanceWriter;

  private ConfigurationService configurationService;

  @Before
  public void init() {
    configurationService = new ConfigurationService();
    mockDecisionDefinitions(new ArrayList<>());
  }

  @Test
  public void testCleanupRunForMultipleDecisionDefinitionsDefaultConfig() {
    // given
    final List<String> decisionDefinitionKeys = generateRandomDefinitionsKeys(3);
    mockDecisionDefinitions(decisionDefinitionKeys);

    //when
    final OptimizeCleanupService underTest = createOptimizeCleanupServiceToTest();
    doCleanup(underTest);

    //then
    assertDeleteDecisionInstancesExecutedFor(decisionDefinitionKeys, getCleanupConfig().getDefaultTtl());
  }

  @Test
  public void testCleanupRunForMultipleDecisionDefinitionsDifferentDefaultTtl() {
    // given
    final Period customTtl = Period.parse("P2M");
    getCleanupConfig().setDefaultTtl(customTtl);
    final List<String> decisionDefinitionKeys = generateRandomDefinitionsKeys(3);

    //when
    mockDecisionDefinitions(decisionDefinitionKeys);
    final OptimizeCleanupService underTest = createOptimizeCleanupServiceToTest();
    doCleanup(underTest);

    //then
    assertDeleteDecisionInstancesExecutedFor(decisionDefinitionKeys, customTtl);
  }

  @Test
  public void testCleanupRunForMultipleDecisionDefinitionsSpecificTtlsOverrideDefault() {
    // given
    final Period customTtl = Period.parse("P2M");
    final List<String> decisionDefinitionKeysWithSpecificTtl = generateRandomDefinitionsKeys(3);
    final Map<String, DecisionDefinitionCleanupConfiguration> decisionDefinitionSpecificConfiguration =
      getCleanupConfig().getDecisionDefinitionSpecificConfiguration();
    decisionDefinitionKeysWithSpecificTtl.forEach(decisionDefinitionKey -> decisionDefinitionSpecificConfiguration.put(
      decisionDefinitionKey, new DecisionDefinitionCleanupConfiguration(customTtl)
    ));
    final List<String> decisionDefinitionKeysWithDefaultTtl = generateRandomDefinitionsKeys(3);
    final List<String> allDecisionDefinitionKeys = ListUtils.union(
      decisionDefinitionKeysWithSpecificTtl,
      decisionDefinitionKeysWithDefaultTtl
    );

    //when
    mockDecisionDefinitions(allDecisionDefinitionKeys);
    final OptimizeCleanupService underTest = createOptimizeCleanupServiceToTest();
    doCleanup(underTest);

    //then
    Map<String, OffsetDateTime> capturedArguments = verifyDeleteDecisionInstanceExecutionReturnCapturedArguments(
      allDecisionDefinitionKeys
    );
    assertKeysWereCalledWithExpectedTtl(capturedArguments, decisionDefinitionKeysWithSpecificTtl, customTtl);
    assertKeysWereCalledWithExpectedTtl(
      capturedArguments, decisionDefinitionKeysWithDefaultTtl, getCleanupConfig().getDefaultTtl()
    );
  }

  @Test
  public void testCleanupRunOnceForEveryDecisionDefinitionKey() {
    // given
    final List<String> decisionDefinitionKeys = generateRandomDefinitionsKeys(3);
    // mock returns keys twice (in reality they have different versions but that doesn't matter for the test)
    mockDecisionDefinitions(ListUtils.union(decisionDefinitionKeys, decisionDefinitionKeys));

    //when
    final OptimizeCleanupService underTest = createOptimizeCleanupServiceToTest();
    doCleanup(underTest);

    //then
    assertDeleteDecisionInstancesExecutedFor(decisionDefinitionKeys, getCleanupConfig().getDefaultTtl());
  }

  @Test
  public void testFailCleanupOnSpecificKeyConfigWithNoMatchingDecisionDefinition() {
    // given I have a key specific config
    final String configuredKey = "myMistypedKey";
    getCleanupConfig().getDecisionDefinitionSpecificConfiguration().put(
      configuredKey,
      new DecisionDefinitionCleanupConfiguration(Period.parse("P2M"))
    );
    // and this key is not present in the known process definition keys
    mockDecisionDefinitions(generateRandomDefinitionsKeys(3));

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

  private void assertDeleteDecisionInstancesExecutedFor(List<String> expectedDecisionDefinitionKeys,
                                                        Period expectedTtl) {
    final Map<String, OffsetDateTime> decisionInstanceKeysWithDateFilter =
      verifyDeleteDecisionInstanceExecutionReturnCapturedArguments(expectedDecisionDefinitionKeys);

    assertKeysWereCalledWithExpectedTtl(
      decisionInstanceKeysWithDateFilter,
      expectedDecisionDefinitionKeys,
      expectedTtl
    );
  }

  private Map<String, OffsetDateTime> verifyDeleteDecisionInstanceExecutionReturnCapturedArguments(
    List<String> expectedDecisionDefinitionKeys) {
    ArgumentCaptor<String> decisionInstanceCaptor = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<OffsetDateTime> evaluationDateFilterCaptor = ArgumentCaptor.forClass(OffsetDateTime.class);
    verify(
      decisionInstanceWriter,
      atLeast(expectedDecisionDefinitionKeys.size())
    ).deleteDecisionInstancesByDefinitionKeyAndEvaluationDateOlderThan(
      decisionInstanceCaptor.capture(),
      evaluationDateFilterCaptor.capture()
    );
    int i = 0;
    final Map<String, OffsetDateTime> filteredDecisionInstancesWithDateFilter = new HashMap<>();
    for (String key : decisionInstanceCaptor.getAllValues()) {
      filteredDecisionInstancesWithDateFilter.put(key, evaluationDateFilterCaptor.getAllValues().get(i));
      i++;
    }
    return filteredDecisionInstancesWithDateFilter;
  }

  private List<String> mockDecisionDefinitions(List<String> decisionDefinitionIds) {
    final List<DecisionDefinitionOptimizeDto> decisionDefinitionOptimizeDtos = decisionDefinitionIds.stream()
      .map(this::createDecisionDefinitionDto)
      .collect(Collectors.toList());
    when(decisionDefinitionReader.fetchFullyImportedDecisionDefinitionsAsService()).thenReturn(
      decisionDefinitionOptimizeDtos);
    return decisionDefinitionIds;
  }

  private List<String> generateRandomDefinitionsKeys(Integer amount) {
    return IntStream.range(0, amount)
      .mapToObj(i -> UUID.randomUUID().toString())
      .collect(toList());
  }

  private DecisionDefinitionOptimizeDto createDecisionDefinitionDto(String key) {
    DecisionDefinitionOptimizeDto decisionDefinitionOptimizeDto = new DecisionDefinitionOptimizeDto().setKey(key);
    return decisionDefinitionOptimizeDto;
  }

  private OptimizeDecisionCleanupService createOptimizeCleanupServiceToTest() {
    return new OptimizeDecisionCleanupService(
      configurationService,
      decisionDefinitionReader,
      decisionInstanceWriter
    );
  }
}
