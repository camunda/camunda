package org.camunda.optimize.service.importing;

import org.camunda.optimize.service.exceptions.OptimizeException;
import org.camunda.optimize.service.importing.impl.ActivityImportService;
import org.camunda.optimize.service.status.ImportProgressReporter;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;

@RunWith(Parameterized.class)
public class ImportProgressReporterTest {

  @Mock
  private EngineEntityFetcher engineEntityFetcher;

  @Mock
  private ImportServiceProvider importServiceProvider;

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
    Mockito.when(engineEntityFetcher.fetchHistoricActivityInstanceCount()).thenReturn(totalEngineEntityCount);
    Mockito.when(engineEntityFetcher.fetchProcessDefinitionCount()).thenReturn(0);
    Mockito.when(engineEntityFetcher.fetchHistoricVariableInstanceCount()).thenReturn(0);
    ActivityImportService importService = Mockito.mock(ActivityImportService.class);
    Mockito.when(importService.getImportStartIndex()).thenReturn(optimizeImportCount);
    Mockito.when(importServiceProvider.getPagedServices()).thenReturn(Collections.singletonList(importService));
  }
}
