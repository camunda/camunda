package org.camunda.optimize.service.es.filter;

import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.ProcessFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.util.ProcessFilterBuilder;
import org.camunda.optimize.dto.optimize.query.report.single.process.result.raw.RawDataProcessReportResultDto;
import org.camunda.optimize.dto.optimize.rest.report.ProcessReportEvaluationResultDto;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.junit.Assert;
import org.junit.Test;

import java.util.List;

import static org.camunda.optimize.test.util.ProcessReportDataBuilderHelper.createProcessReportDataViewRawAsTable;
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
      createProcessReportDataViewRawAsTable(processInstance.getProcessDefinitionKey(), processInstance.getProcessDefinitionVersion());
    List<ProcessFilterDto> gte = ProcessFilterBuilder
      .filter()
      .duration()
      .unit("Seconds")
      .value((long) 2)
      .operator(">=")
      .add()
      .buildList();
    List<ProcessFilterDto> lt = ProcessFilterBuilder
      .filter()
      .duration()
      .unit("Days")
      .value((long) 1)
      .operator("<")
      .add()
      .buildList();
    gte.addAll(lt);
    reportData.setFilter(gte);
    ProcessReportEvaluationResultDto<RawDataProcessReportResultDto> result = evaluateReport(reportData);

    // then
    assertResult(processInstance, result);
  }

  @Test
  public void testValidationExceptionOnNullFilterField() throws Exception {
    // given
    ProcessInstanceEngineDto processInstance = deployAndStartSimpleProcess();
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    ProcessReportDataDto reportData =
      createProcessReportDataViewRawAsTable(processInstance.getProcessDefinitionKey(), processInstance.getProcessDefinitionVersion());
    reportData.setFilter(ProcessFilterBuilder
                           .filter()
                           .duration()
                           .unit(null)
                           .value((long) 2)
                           .operator(">=")
                           .add()
                           .buildList());


    Assert.assertThat(evaluateReportAndReturnResponse(reportData).getStatus(),is(500));
  }

}
