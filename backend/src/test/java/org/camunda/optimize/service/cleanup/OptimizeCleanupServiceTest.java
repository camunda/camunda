package org.camunda.optimize.service.cleanup;

import org.apache.commons.collections.ListUtils;
import org.camunda.optimize.dto.optimize.importing.ProcessDefinitionOptimizeDto;
import org.camunda.optimize.service.es.reader.ProcessDefinitionReader;
import org.camunda.optimize.service.es.writer.FinishedProcessInstanceWriter;
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
import org.mockito.runners.MockitoJUnitRunner;

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
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class OptimizeCleanupServiceTest {

  @Mock
  private ProcessDefinitionReader processDefinitionReader;
  @Mock
  private FinishedProcessInstanceWriter processInstanceWriter;
  @Mock
  private VariableWriter variableWriter;

  private ConfigurationService configurationService;

  @Before
  public void init() {
    final String[] locations = {"service-config.yaml", "cleanup-test-config.yaml"};
    configurationService = new ConfigurationService(locations);
  }

  @Test
  public void testCleanupRunForMultipleProcessDefinitionsDefaultConfig() {
    // given
    final List<String> processDefinitionKeys = generateRandomProcessDefinitionsKeys(3);
    mockProcessDefinitions(processDefinitionKeys);

    //when
    final OptimizeCleanupService underTest = createOptimizeCleanupServiceToTest();
    underTest.runCleanup();

    //then
    assertDeleteProcessInstancesExecutedFor(processDefinitionKeys, getCleanupConfig().getDefaultTtl());
  }

  @Test
  public void testCleanupRunForMultipleProcessDefinitionsDifferentDefaultMode() {
    // given
    final CleanupMode customMode = CleanupMode.VARIABLES;
    getCleanupConfig().setDefaultMode(customMode);
    final List<String> processDefinitionKeys = generateRandomProcessDefinitionsKeys(3);

    //when
    mockProcessDefinitions(processDefinitionKeys);
    final OptimizeCleanupService underTest = createOptimizeCleanupServiceToTest();
    underTest.runCleanup();

    //then
    assertDeleteAllInstanceVariablesExecutedFor(processDefinitionKeys, getCleanupConfig().getDefaultTtl());
  }

  private OptimizeCleanupConfiguration getCleanupConfig() {
    return configurationService.getCleanupServiceConfiguration();
  }

  @Test
  public void testCleanupRunForMultipleProcessDefinitionsDifferentDefaultTtl() {
    // given
    final Period customTtl = Period.parse("P2M");
    getCleanupConfig().setDefaultTtl(customTtl);
    final List<String> processDefinitionKeys = generateRandomProcessDefinitionsKeys(3);

    //when
    mockProcessDefinitions(processDefinitionKeys);
    final OptimizeCleanupService underTest = createOptimizeCleanupServiceToTest();
    underTest.runCleanup();

    //then
    assertDeleteProcessInstancesExecutedFor(processDefinitionKeys, customTtl);
  }

  @Test
  public void testCleanupRunForMultipleProcessDefinitionsSpecificModeOverridesDefault() {
    // given
    final CleanupMode customMode = CleanupMode.VARIABLES;
    final List<String> processDefinitionKeysWithSpecificMode = generateRandomProcessDefinitionsKeys(3);
    Map<String, ProcessDefinitionCleanupConfiguration> processDefinitionSpecificConfiguration =
      getCleanupConfig().getProcessDefinitionSpecificConfiguration();
    processDefinitionKeysWithSpecificMode.forEach(processDefinitionKey -> processDefinitionSpecificConfiguration.put(
      processDefinitionKey, new ProcessDefinitionCleanupConfiguration(customMode)
    ));
    final List<String> processDefinitionKeysWithDefaultMode = generateRandomProcessDefinitionsKeys(3);
    final List allProcessDefinitionKeys = ListUtils.union(
      processDefinitionKeysWithSpecificMode,
      processDefinitionKeysWithDefaultMode
    );

    //when
    mockProcessDefinitions(allProcessDefinitionKeys);
    final OptimizeCleanupService underTest = createOptimizeCleanupServiceToTest();
    underTest.runCleanup();

    //then
    verifyDeleteProcessInstanceExecutionReturnCapturedArguments(processDefinitionKeysWithDefaultMode);
    verifyDeleteAllInstanceVariablesReturnCapturedArguments(processDefinitionKeysWithSpecificMode);
  }

  @Test
  public void testCleanupRunForMultipleProcessDefinitionsSpecificTtlsOverrideDefault() {
    // given
    final Period customTtl = Period.parse("P2M");
    final List<String> processDefinitionKeysWithSpecificTtl = generateRandomProcessDefinitionsKeys(3);
    Map<String, ProcessDefinitionCleanupConfiguration> processDefinitionSpecificConfiguration =
      getCleanupConfig().getProcessDefinitionSpecificConfiguration();
    processDefinitionKeysWithSpecificTtl.forEach(processDefinitionKey -> processDefinitionSpecificConfiguration.put(
      processDefinitionKey, new ProcessDefinitionCleanupConfiguration(customTtl)
    ));
    final List<String> processDefinitionKeysWithDefaultTtl = generateRandomProcessDefinitionsKeys(3);
    final List allProcessDefinitionKeys = ListUtils.union(
      processDefinitionKeysWithSpecificTtl,
      processDefinitionKeysWithDefaultTtl
    );

    //when
    mockProcessDefinitions(allProcessDefinitionKeys);
    final OptimizeCleanupService underTest = createOptimizeCleanupServiceToTest();
    underTest.runCleanup();

    //then
    Map<String, OffsetDateTime> capturedArguments = verifyDeleteProcessInstanceExecutionReturnCapturedArguments(
      allProcessDefinitionKeys
    );
    assertKeysWereCalledWithExpectedTtl(capturedArguments, processDefinitionKeysWithSpecificTtl, customTtl);
    assertKeysWereCalledWithExpectedTtl(
      capturedArguments, processDefinitionKeysWithDefaultTtl, getCleanupConfig().getDefaultTtl()
    );
  }

  @Test(expected = OptimizeConfigurationException.class)
  public void testFailInitOnInvalidConfig() {
    // given
    getCleanupConfig().setDefaultTtl(null);

    //when
    final OptimizeCleanupService underTest = createOptimizeCleanupServiceToTest();
    underTest.init();

    //then
  }

  @Test
  public void testCleanupRunOnceForEveryProcessDefinitionKey() {
    // given
    final List<String> processDefinitionKeys = generateRandomProcessDefinitionsKeys(3);
    // mock returns keys twice (in reality they have different versions but that doesn't matter for the test)
    mockProcessDefinitions(ListUtils.union(processDefinitionKeys, processDefinitionKeys));

    //when
    final OptimizeCleanupService underTest = createOptimizeCleanupServiceToTest();
    underTest.runCleanup();

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
    generateRandomProcessDefinitionsKeys(3);

    //when I run the cleanup
    final OptimizeCleanupService underTest = createOptimizeCleanupServiceToTest();
    OptimizeConfigurationException expectedException = null;
    try {
      underTest.runCleanup();
    } catch (OptimizeConfigurationException e) {
      expectedException = e;
    }

    //then it fails with an exception
    MatcherAssert.assertThat(expectedException, CoreMatchers.is((notNullValue())));
    MatcherAssert.assertThat(expectedException.getMessage(), containsString(configuredKey));
  }

  private void assertDeleteProcessInstancesExecutedFor(List<String> expectedProcessDefinitionKeys, Period expectedTtl) {
    final Map<String, OffsetDateTime> processInstanceKeysWithDateFilter =
      verifyDeleteProcessInstanceExecutionReturnCapturedArguments(expectedProcessDefinitionKeys);

    assertKeysWereCalledWithExpectedTtl(processInstanceKeysWithDateFilter, expectedProcessDefinitionKeys, expectedTtl);
  }

  private void assertKeysWereCalledWithExpectedTtl(Map<String, OffsetDateTime> capturedInvocationArguments,
                                                   List<String> expectedProcessDefinitionKeys,
                                                   Period expectedTtl) {
    Map<String, OffsetDateTime> filteredInvocationArguments = capturedInvocationArguments.entrySet().stream()
      .filter(entry -> expectedProcessDefinitionKeys.contains(entry.getKey()))
      .collect(toMap(Map.Entry::getKey, Map.Entry::getValue));
    assertThat(filteredInvocationArguments.size(), is(expectedProcessDefinitionKeys.size()));
    final OffsetDateTime endDateFilterValue = filteredInvocationArguments.values()
      .toArray(new OffsetDateTime[]{})[0];
    assertThat(endDateFilterValue, lessThanOrEqualTo(OffsetDateTime.now().minus(expectedTtl)));
    filteredInvocationArguments.values().forEach(instant -> assertThat(instant, is(endDateFilterValue)));
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
    when(processDefinitionReader.getProcessDefinitionsAsService()).thenReturn(processDefinitionOptimizeDtos);
    return processDefinitionIds;
  }

  private List<String> generateRandomProcessDefinitionsKeys(Integer amount) {
    return IntStream.range(0, amount)
      .mapToObj(i -> UUID.randomUUID().toString())
      .collect(toList());
  }

  private ProcessDefinitionOptimizeDto createProcessDefinitionDto(String key) {
    ProcessDefinitionOptimizeDto processDefinitionOptimizeDto = new ProcessDefinitionOptimizeDto();
    processDefinitionOptimizeDto.setKey(key);
    return processDefinitionOptimizeDto;
  }

  private OptimizeCleanupService createOptimizeCleanupServiceToTest() {
    return new OptimizeCleanupService(
      configurationService,
      processDefinitionReader,
      processInstanceWriter,
      variableWriter
    );
  }
}
