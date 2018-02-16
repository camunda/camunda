package org.camunda.optimize.service.es.filter;

import org.camunda.optimize.dto.optimize.query.report.result.raw.RawDataReportResultDto;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestContextManager;

import java.util.Arrays;
import java.util.Collection;

/**
 * @author Askar Akhmerov
 */
@RunWith(Parameterized.class)
@ContextConfiguration(locations = {"/it/it-applicationContext.xml"})
public class RollingDateFilterUnitsIT extends AbstractRollingDateFilterIT {

  private TestContextManager testContextManager;

  @Parameterized.Parameters
  public static Collection<Object[]> data() {
    return Arrays.asList(new Object[][] {
        { "days", 1 }, { "minutes", 1 }, { "hours", 1 }, { "weeks", 1 }, { "months", 1 }, { "nanos", 0 }
    });
  }

  private String unit;
  private int expectedPiCount;

  public RollingDateFilterUnitsIT(String unit, int expectedPiCount) {
    this.unit = unit;
    this.expectedPiCount = expectedPiCount;
  }

  @Before
  public void setUpContext() throws Exception {
    //this is where the magic happens, we actually do "by hand" what the spring runner would do for us,
    // read the JavaDoc for the class bellow to know exactly what it does, the method names are quite accurate though
    this.testContextManager = new TestContextManager(getClass());
    this.testContextManager.prepareTestInstance(this);
  }

  @Test
  public void rollingDateFilterInReport() throws Exception {
    // given
    ProcessInstanceEngineDto processInstance = deployAndStartSimpleProcess();
    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    // when
    RawDataReportResultDto result = createAndEvaluateReport(
        processInstance.getProcessDefinitionKey(),
        processInstance.getProcessDefinitionVersion(),
        unit,
        false
    );

    //then
    assertResults(processInstance, result, expectedPiCount);
  }


}
