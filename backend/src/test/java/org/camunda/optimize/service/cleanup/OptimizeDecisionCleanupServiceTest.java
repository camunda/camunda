/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.cleanup;

import org.apache.commons.collections4.SetUtils;
import org.camunda.optimize.service.es.reader.DecisionInstanceReader;
import org.camunda.optimize.service.es.writer.DecisionInstanceWriter;
import org.camunda.optimize.service.exceptions.OptimizeConfigurationException;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.camunda.optimize.service.util.configuration.ConfigurationServiceBuilder;
import org.camunda.optimize.service.util.configuration.cleanup.CleanupConfiguration;
import org.camunda.optimize.service.util.configuration.cleanup.DecisionDefinitionCleanupConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.time.Period;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.IntStream;

import static java.util.stream.Collectors.toMap;
import static java.util.stream.Collectors.toSet;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class OptimizeDecisionCleanupServiceTest {

  @Mock
  private DecisionInstanceReader decisionInstanceReader;
  @Mock
  private DecisionInstanceWriter decisionInstanceWriter;

  private ConfigurationService configurationService;

  @BeforeEach
  public void init() {
    configurationService = ConfigurationServiceBuilder.createDefaultConfiguration();
  }

  @Test
  public void testCleanupRunForMultipleDecisionDefinitionsDefaultConfig() {
    // given
    final Set<String> decisionDefinitionKeys = generateRandomDefinitionsKeys(3);
    mockDecisionDefinitions(decisionDefinitionKeys);

    // when
    final CleanupService underTest = createOptimizeCleanupServiceToTest();
    doCleanup(underTest);

    // then
    assertDeleteDecisionInstancesExecutedFor(decisionDefinitionKeys, getCleanupConfiguration().getTtl());
  }

  @Test
  public void testCleanupRunForMultipleDecisionDefinitionsDifferentDefaultTtl() {
    // given
    final Period customTtl = Period.parse("P2M");
    getCleanupConfiguration().setTtl(customTtl);
    final Set<String> decisionDefinitionKeys = generateRandomDefinitionsKeys(3);

    // when
    mockDecisionDefinitions(decisionDefinitionKeys);
    final CleanupService underTest = createOptimizeCleanupServiceToTest();
    doCleanup(underTest);

    // then
    assertDeleteDecisionInstancesExecutedFor(decisionDefinitionKeys, customTtl);
  }

  @Test
  public void testCleanupRunForMultipleDecisionDefinitionsSpecificTtlsOverrideDefault() {
    // given
    final Period customTtl = Period.parse("P2M");
    final Set<String> decisionDefinitionKeysWithSpecificTtl = generateRandomDefinitionsKeys(3);
    final Map<String, DecisionDefinitionCleanupConfiguration> decisionDefinitionSpecificConfiguration =
      getCleanupConfiguration().getDecisionCleanupConfiguration().getDecisionDefinitionSpecificConfiguration();
    decisionDefinitionKeysWithSpecificTtl.forEach(decisionDefinitionKey -> decisionDefinitionSpecificConfiguration.put(
      decisionDefinitionKey, new DecisionDefinitionCleanupConfiguration(customTtl)
    ));
    final Set<String> decisionDefinitionKeysWithDefaultTtl = generateRandomDefinitionsKeys(3);
    final Set<String> allDecisionDefinitionKeys = SetUtils.union(
      decisionDefinitionKeysWithSpecificTtl,
      decisionDefinitionKeysWithDefaultTtl
    );

    // when
    mockDecisionDefinitions(allDecisionDefinitionKeys);
    final CleanupService underTest = createOptimizeCleanupServiceToTest();
    doCleanup(underTest);

    // then
    Map<String, OffsetDateTime> capturedArguments = verifyDeleteDecisionInstanceExecutionReturnCapturedArguments(
      allDecisionDefinitionKeys
    );
    assertKeysWereCalledWithExpectedTtl(capturedArguments, decisionDefinitionKeysWithSpecificTtl, customTtl);
    assertKeysWereCalledWithExpectedTtl(
      capturedArguments, decisionDefinitionKeysWithDefaultTtl, getCleanupConfiguration().getTtl()
    );
  }

  @Test
  public void testCleanupRunOnceForEveryDecisionDefinitionKey() {
    // given
    final Set<String> decisionDefinitionKeys = generateRandomDefinitionsKeys(3);
    // mock returns keys twice (in reality they have different versions but that doesn't matter for the test)
    mockDecisionDefinitions(SetUtils.union(decisionDefinitionKeys, decisionDefinitionKeys));

    // when
    final CleanupService underTest = createOptimizeCleanupServiceToTest();
    doCleanup(underTest);

    // then
    assertDeleteDecisionInstancesExecutedFor(decisionDefinitionKeys, getCleanupConfiguration().getTtl());
  }

  @Test
  public void testFailCleanupOnSpecificKeyConfigWithNoMatchingDecisionDefinition() {
    // given I have a key specific config
    final String configuredKey = "myMistypedKey";
    getCleanupConfiguration()
      .getDecisionCleanupConfiguration()
      .getDecisionDefinitionSpecificConfiguration()
      .put(
        configuredKey,
        new DecisionDefinitionCleanupConfiguration(Period.parse("P2M"))
      );
    // and this key is not present in the known process definition keys
    mockDecisionDefinitions(generateRandomDefinitionsKeys(3));

    // when I run the cleanup then it fails with an exception
    OptimizeConfigurationException exception =
      assertThrows(OptimizeConfigurationException.class, () -> doCleanup(createOptimizeCleanupServiceToTest()));
    assertThat(exception.getMessage()).contains(configuredKey);
  }

  private void doCleanup(final CleanupService underTest) {
    underTest.doCleanup(OffsetDateTime.now());
  }

  private CleanupConfiguration getCleanupConfiguration() {
    return configurationService.getCleanupServiceConfiguration();
  }

  private void assertKeysWereCalledWithExpectedTtl(Map<String, OffsetDateTime> capturedInvocationArguments,
                                                   Set<String> expectedDefinitionKeys,
                                                   Period expectedTtl) {
    final Map<String, OffsetDateTime> filteredInvocationArguments = capturedInvocationArguments.entrySet().stream()
      .filter(entry -> expectedDefinitionKeys.contains(entry.getKey()))
      .collect(toMap(Map.Entry::getKey, Map.Entry::getValue));
    assertThat(filteredInvocationArguments).hasSameSizeAs(expectedDefinitionKeys);

    final OffsetDateTime dateFilterValue = filteredInvocationArguments.values().toArray(new OffsetDateTime[]{})[0];
    assertThat(dateFilterValue).isBeforeOrEqualTo(OffsetDateTime.now().minus(expectedTtl));
    filteredInvocationArguments.values().forEach(instant -> assertThat(instant).isEqualTo(dateFilterValue));
  }

  private void assertDeleteDecisionInstancesExecutedFor(Set<String> expectedDecisionDefinitionKeys,
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
    Set<String> expectedDecisionDefinitionKeys) {
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

  private Set<String> mockDecisionDefinitions(Set<String> decisionDefinitionKeys) {
    when(decisionInstanceReader.getExistingDecisionDefinitionKeysFromInstances())
      .thenReturn(decisionDefinitionKeys);
    return decisionDefinitionKeys;
  }

  private Set<String> generateRandomDefinitionsKeys(Integer amount) {
    return IntStream.range(0, amount)
      .mapToObj(i -> UUID.randomUUID().toString())
      .collect(toSet());
  }

  private EngineDataDecisionCleanupService createOptimizeCleanupServiceToTest() {
    return new EngineDataDecisionCleanupService(
      configurationService,
      decisionInstanceReader,
      decisionInstanceWriter
    );
  }
}
