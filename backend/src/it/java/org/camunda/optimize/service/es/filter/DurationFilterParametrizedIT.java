package org.camunda.optimize.service.es.filter;

import org.camunda.optimize.dto.optimize.query.report.ReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.result.raw.RawDataReportResultDto;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.camunda.optimize.test.util.DateUtilHelper;
import org.camunda.optimize.test.util.ReportDataHelper;
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
public class DurationFilterParametrizedIT extends AbstractDurationFilterIT {

  private TestContextManager testContextManager;

  @Before
  public void setUpContext() throws Exception {
    //this is where the magic happens, we actually do "by hand" what the spring runner would do for us,
    // read the JavaDoc for the class bellow to know exactly what it does, the method names are quite accurate though
    this.testContextManager = new TestContextManager(getClass());
    this.testContextManager.prepareTestInstance(this);
  }

  @Parameterized.Parameters
  public static Collection<Object[]> data() {
    return Arrays.asList(new Object[][] {
        { false, null, null, "<", 1, "Seconds" },
        { false, null, null, "<", 1, "Minutes" },
        { false, null, null, "<", 1, "Hours" },
        { false, null, null, "<", 1, "Half_Days" },
        { false, null, null, "<", 1, "Days" },
        { false, null, null, "<", 1, "Weeks" },
        { false, null, null, "<", 1, "Months" },
        { false, null, null, "=<", 1, "Days" },
        { true, 0L, 2L, ">", 1, "Seconds" },
        { true, 0L, 2L, ">=", 2, "Seconds" }
    });
  }

  private boolean deployWithTimeShift;
  private Long daysToShift;
  private Long durationInSec;
  private String operator;
  private int duration;
  private String unit;

  public DurationFilterParametrizedIT(
      boolean deployWithTimeShift,
      Long daysToShift,
      Long durationInSec,
      String operator,
      int duration,
      String unit
  ) {

    this.deployWithTimeShift = deployWithTimeShift;
    this.daysToShift = daysToShift;
    this.durationInSec = durationInSec;
    this.operator = operator;
    this.duration = duration;
    this.unit = unit;
  }

  @Test
  public void testGetReportWithLtDurationCriteria () throws Exception {
    // given
    ProcessInstanceEngineDto processInstance;
    if (this.deployWithTimeShift) {
      processInstance = deployWithTimeShift(this.daysToShift, this.durationInSec);
    } else {
      processInstance = deployAndStartSimpleProcess();
    }

    String processDefinitionId = processInstance.getDefinitionId();
    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    // when
    ReportDataDto reportData = ReportDataHelper.createReportDataViewRawAsTable(processDefinitionId);
    reportData.setFilter(DateUtilHelper.createDurationFilter(this.operator, this.duration, this.unit));
    RawDataReportResultDto result = evaluateReport(reportData);

    // then
    assertResult(processInstance, processDefinitionId, result);
  }



}
