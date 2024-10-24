/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.cleanup;

import static java.util.stream.Collectors.toMap;
import static java.util.stream.Collectors.toSet;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.optimize.dto.optimize.DecisionDefinitionOptimizeDto;
import io.camunda.optimize.service.db.reader.DecisionDefinitionReader;
import io.camunda.optimize.service.db.writer.DecisionInstanceWriter;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import io.camunda.optimize.service.util.configuration.ConfigurationServiceBuilder;
import io.camunda.optimize.service.util.configuration.cleanup.CleanupConfiguration;
import io.camunda.optimize.service.util.configuration.cleanup.DecisionDefinitionCleanupConfiguration;
import io.github.netmikey.logunit.api.LogCapturer;
import java.time.OffsetDateTime;
import java.time.Period;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.IntStream;
import org.apache.commons.collections4.SetUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class OptimizeDecisionCleanupServiceTest {

  @RegisterExtension
  LogCapturer logCapturer = LogCapturer.create().captureForType(CleanupService.class);

  @Mock private DecisionDefinitionReader decisionDefinitionReader;
  @Mock private DecisionInstanceWriter decisionInstanceWriter;
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
    assertDeleteDecisionInstancesExecutedFor(
        decisionDefinitionKeys, getCleanupConfiguration().getTtl());
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
    final Map<String, DecisionDefinitionCleanupConfiguration>
        decisionDefinitionSpecificConfiguration =
            getCleanupConfiguration()
                .getDecisionCleanupConfiguration()
                .getDecisionDefinitionSpecificConfiguration();
    decisionDefinitionKeysWithSpecificTtl.forEach(
        decisionDefinitionKey ->
            decisionDefinitionSpecificConfiguration.put(
                decisionDefinitionKey, new DecisionDefinitionCleanupConfiguration(customTtl)));
    final Set<String> decisionDefinitionKeysWithDefaultTtl = generateRandomDefinitionsKeys(3);
    final Set<String> allDecisionDefinitionKeys =
        SetUtils.union(decisionDefinitionKeysWithSpecificTtl, decisionDefinitionKeysWithDefaultTtl);

    // when
    mockDecisionDefinitions(allDecisionDefinitionKeys);
    final CleanupService underTest = createOptimizeCleanupServiceToTest();
    doCleanup(underTest);

    // then
    final Map<String, OffsetDateTime> capturedArguments =
        verifyDeleteDecisionInstanceExecutionReturnCapturedArguments(allDecisionDefinitionKeys);
    assertKeysWereCalledWithExpectedTtl(
        capturedArguments, decisionDefinitionKeysWithSpecificTtl, customTtl);
    assertKeysWereCalledWithExpectedTtl(
        capturedArguments,
        decisionDefinitionKeysWithDefaultTtl,
        getCleanupConfiguration().getTtl());
  }

  @Test
  public void testCleanupRunOnceForEveryDecisionDefinitionKey() {
    // given
    final Set<String> decisionDefinitionKeys = generateRandomDefinitionsKeys(3);
    // mock returns keys twice (in reality they have different versions but that doesn't matter for
    // the test)
    mockDecisionDefinitions(SetUtils.union(decisionDefinitionKeys, decisionDefinitionKeys));

    // when
    final CleanupService underTest = createOptimizeCleanupServiceToTest();
    doCleanup(underTest);

    // then
    assertDeleteDecisionInstancesExecutedFor(
        decisionDefinitionKeys, getCleanupConfiguration().getTtl());
  }

  @Test
  public void testWarnOnCleanupOnSpecificKeyConfigWithNoMatchingDecisionDefinition() {
    // given I have a key specific config
    final String misconfiguredKey = "myMistypedKey";
    getCleanupConfiguration()
        .getDecisionCleanupConfiguration()
        .getDecisionDefinitionSpecificConfiguration()
        .put(misconfiguredKey, new DecisionDefinitionCleanupConfiguration(Period.parse("P2M")));
    // and this key is not present in the known decision definition keys
    final Set<String> decisionDefinitionKeys = generateRandomDefinitionsKeys(3);
    mockDecisionDefinitions(decisionDefinitionKeys);

    // when
    final CleanupService underTest = createOptimizeCleanupServiceToTest();
    doCleanup(underTest);

    // then
    assertDeleteDecisionInstancesExecutedFor(
        decisionDefinitionKeys, getCleanupConfiguration().getTtl());

    // and it warns on misconfigured keys
    logCapturer.assertContains(
        String.format(
            "History Cleanup Configuration contains definition keys for which there is no "
                + "definition imported yet. The keys without a match in the database are: [%s]",
            misconfiguredKey));
  }

  private void doCleanup(final CleanupService underTest) {
    underTest.doCleanup(OffsetDateTime.now());
  }

  private CleanupConfiguration getCleanupConfiguration() {
    return configurationService.getCleanupServiceConfiguration();
  }

  private void assertKeysWereCalledWithExpectedTtl(
      final Map<String, OffsetDateTime> capturedInvocationArguments,
      final Set<String> expectedDefinitionKeys,
      final Period expectedTtl) {
    final Map<String, OffsetDateTime> filteredInvocationArguments =
        capturedInvocationArguments.entrySet().stream()
            .filter(entry -> expectedDefinitionKeys.contains(entry.getKey()))
            .collect(toMap(Map.Entry::getKey, Map.Entry::getValue));
    assertThat(filteredInvocationArguments).hasSameSizeAs(expectedDefinitionKeys);

    final OffsetDateTime dateFilterValue =
        filteredInvocationArguments.values().toArray(new OffsetDateTime[] {})[0];
    assertThat(dateFilterValue).isBeforeOrEqualTo(OffsetDateTime.now().minus(expectedTtl));
    filteredInvocationArguments
        .values()
        .forEach(instant -> assertThat(instant).isEqualTo(dateFilterValue));
  }

  private void assertDeleteDecisionInstancesExecutedFor(
      final Set<String> expectedDecisionDefinitionKeys, final Period expectedTtl) {
    final Map<String, OffsetDateTime> decisionInstanceKeysWithDateFilter =
        verifyDeleteDecisionInstanceExecutionReturnCapturedArguments(
            expectedDecisionDefinitionKeys);

    assertKeysWereCalledWithExpectedTtl(
        decisionInstanceKeysWithDateFilter, expectedDecisionDefinitionKeys, expectedTtl);
  }

  private Map<String, OffsetDateTime> verifyDeleteDecisionInstanceExecutionReturnCapturedArguments(
      final Set<String> expectedDecisionDefinitionKeys) {
    final ArgumentCaptor<String> decisionInstanceCaptor = ArgumentCaptor.forClass(String.class);
    final ArgumentCaptor<OffsetDateTime> evaluationDateFilterCaptor =
        ArgumentCaptor.forClass(OffsetDateTime.class);
    verify(decisionInstanceWriter, atLeast(expectedDecisionDefinitionKeys.size()))
        .deleteDecisionInstancesByDefinitionKeyAndEvaluationDateOlderThan(
            decisionInstanceCaptor.capture(), evaluationDateFilterCaptor.capture());
    int i = 0;
    final Map<String, OffsetDateTime> filteredDecisionInstancesWithDateFilter = new HashMap<>();
    for (final String key : decisionInstanceCaptor.getAllValues()) {
      filteredDecisionInstancesWithDateFilter.put(
          key, evaluationDateFilterCaptor.getAllValues().get(i));
      i++;
    }
    return filteredDecisionInstancesWithDateFilter;
  }

  private void mockDecisionDefinitions(final Set<String> decisionDefinitionKeys) {
    when(decisionDefinitionReader.getAllDecisionDefinitions())
        .thenReturn(
            decisionDefinitionKeys.stream()
                .map(
                    defKeys -> {
                      final DecisionDefinitionOptimizeDto def = new DecisionDefinitionOptimizeDto();
                      def.setKey(defKeys);
                      return def;
                    })
                .toList());
  }

  private Set<String> generateRandomDefinitionsKeys(final Integer amount) {
    return IntStream.range(0, amount).mapToObj(i -> UUID.randomUUID().toString()).collect(toSet());
  }

  private EngineDataDecisionCleanupService createOptimizeCleanupServiceToTest() {
    return new EngineDataDecisionCleanupService(
        configurationService, decisionDefinitionReader, decisionInstanceWriter);
  }
}
