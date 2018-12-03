package org.camunda.optimize.service.es.filter;

import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.ProcessFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.result.raw.RawDataProcessReportResultDto;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.camunda.optimize.test.util.DateUtilHelper;
import org.junit.Assert;
import org.junit.Test;

import java.util.List;

import static org.camunda.optimize.test.util.ReportDataBuilderHelper.createReportDataViewRawAsTable;
import static org.hamcrest.CoreMatchers.is;



public class DurationFilterIT extends AbstractDurationFilterIT {

  @Test
  public void testGetReportWithMixedDurationCriteria () throws Exception {
    // given
    long daysToShift = 0L;
    long durationInSec = 2L;

    ProcessInstanceEngineDto processInstance = deployWithTimeShift(daysToShift, durationInSec);

    // when
    ProcessReportDataDto reportData =
      createReportDataViewRawAsTable(processInstance.getProcessDefinitionKey(), processInstance.getProcessDefinitionVersion());
    List<ProcessFilterDto> gte = DateUtilHelper.createDurationFilter(">=", 2, "Seconds");
    List<ProcessFilterDto> lt = DateUtilHelper.createDurationFilter("<", 1, "Days");
    gte.addAll(lt);
    reportData.setFilter(gte);
    RawDataProcessReportResultDto result = evaluateReport(reportData);

    // then
    assertResult(processInstance, result);
  }

  @Test
  public void testValidationExceptionOnNullFilterField() throws Exception {
    // given
    ProcessInstanceEngineDto processInstance = deployAndStartSimpleProcess();
    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    ProcessReportDataDto reportData =
      createReportDataViewRawAsTable(processInstance.getProcessDefinitionKey(), processInstance.getProcessDefinitionVersion());
    reportData.setFilter(DateUtilHelper.createDurationFilter(">=", 2, null));


    Assert.assertThat(evaluateReportAndReturnResponse(reportData).getStatus(),is(500));
  }

}
