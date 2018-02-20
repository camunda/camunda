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
public class RollingDateFilterUnitsIT extends AbstractRollingDateFilterIT {

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
