package org.camunda.optimize.service.importing;

import org.camunda.optimize.dto.optimize.query.ConnectionStatusDto;
import org.camunda.optimize.service.exceptions.OptimizeException;
import org.camunda.optimize.service.importing.impl.ActivityImportService;
import org.camunda.optimize.service.importing.index.DefinitionBasedImportIndexHandler;
import org.camunda.optimize.service.importing.index.ImportIndexHandler;
import org.camunda.optimize.service.importing.provider.ImportServiceProvider;
import org.camunda.optimize.service.importing.provider.IndexHandlerProvider;
import org.camunda.optimize.service.status.ImportProgressReporter;
import org.camunda.optimize.service.status.StatusCheckingService;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.camunda.optimize.service.util.configuration.EngineConfiguration;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.mockito.ArgumentMatchers.any;

@RunWith(Parameterized.class)
public class ImportProgressReporterTest {

  private static final String TEST_ENGINE = "test-engine";
  @Mock
  private ActivityImportService activityImportService;

  @Mock
  private ImportServiceProvider importServiceProvider;

  @Mock
  private IndexHandlerProvider indexHandlerProvider;

  @Mock
  private ConfigurationService configurationService;

  @Mock
  private StatusCheckingService statusCheckingService;

  @InjectMocks
  private ImportProgressReporter reporter = new ImportProgressReporter();

  private int optimizeImportCount;
  private int totalEngineEntityCount;
  private int expectedProgressResult;

  @Before
  public void setup() throws Exception {
    MockitoAnnotations.initMocks(this);
  }

  @Parameterized.Parameters(name = "{index}: optimizeImportCount={0}, totalEngineEntityCount={1}, expectedProgressResult={2}")
  public static Collection<Object[]> data() {
    return Arrays.asList(new Object[][]{
      {0, 0, 0},
      {10, 10, 100},
      {12, 10, 100},
      {6, 10, 60},
      {10, 0, 0},
      {0, 10, 0},
      {3, 9, 33}
    });
  }

  public ImportProgressReporterTest(int optimizeImportCount, int totalEngineEntityCount, int expectedProgressResult) {
    this.optimizeImportCount = optimizeImportCount;
    this.totalEngineEntityCount = totalEngineEntityCount;
    this.expectedProgressResult = expectedProgressResult;
  }

  @Test
  public void testImportProgressReporter() throws OptimizeException {
    // given
    initializeMocks();

    // when
    int actualResult = reporter.computeImportProgress();

    // then
    assertThat(actualResult, is(expectedProgressResult));

  }

  private void initializeMocks() throws OptimizeException {
    DefinitionBasedImportIndexHandler indexHandler = Mockito.mock(DefinitionBasedImportIndexHandler.class);
        Mockito.when(
            indexHandlerProvider.getIndexHandler(
                activityImportService.getElasticsearchType(),
                activityImportService.getIndexHandlerType(),
                TEST_ENGINE
            )
        ).thenReturn(indexHandler);
    Collection<ImportIndexHandler> list = new ArrayList<>();
    list.add(indexHandler);
    mockstatusCheckingService();
    Mockito.when(indexHandlerProvider.getAllHandlersForAliases(any())).thenReturn(list);
    Mockito.when(activityImportService.getEngineEntityCount(indexHandler, TEST_ENGINE)).thenReturn(totalEngineEntityCount);
    Mockito.when(importServiceProvider.getPagedServices()).thenReturn(Collections.singletonList(activityImportService));
    Mockito.when(indexHandler.getAbsoluteImportIndex()).thenReturn(optimizeImportCount);

    Mockito.when(configurationService.getConfiguredEngines()).thenReturn(engineConfigsToReturn());
  }

  private void mockstatusCheckingService() {
    Map<String, Boolean> engineConnections = new HashMap<>();
    engineConnections.put(TEST_ENGINE, true);
    ConnectionStatusDto connectionStatusDto = new ConnectionStatusDto();
    connectionStatusDto.setEngineConnections(engineConnections);
    Mockito.when(statusCheckingService.getConnectionStatus()).thenReturn(connectionStatusDto);
  }

  private Map<String, EngineConfiguration> engineConfigsToReturn() {
    HashMap<String, EngineConfiguration> engines = new HashMap<>();
    EngineConfiguration engineConfig = new EngineConfiguration();
    engines.put(TEST_ENGINE, engineConfig);
    return engines;
  }
}
