package org.camunda.optimize.service.es.filter;

import org.camunda.optimize.dto.optimize.query.report.single.process.result.raw.RawDataProcessReportResultDto;
import org.camunda.optimize.dto.optimize.rest.report.ProcessReportEvaluationResultDto;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.camunda.optimize.service.security.util.LocalDateUtil;
import org.junit.Test;

import java.time.OffsetDateTime;



public class RollingDateFilterIT extends AbstractRollingDateFilterIT {

  @Test
  public void testStartDateRollingLogic() {
    // given
    embeddedOptimizeRule.reloadConfiguration();
    ProcessInstanceEngineDto processInstance = deployAndStartSimpleProcess();

    OffsetDateTime processInstanceStartTime =
        engineRule.getHistoricProcessInstance(processInstance.getId()).getStartTime();

    engineRule.finishAllUserTasks(processInstance.getId());

    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    LocalDateUtil.setCurrentTime(processInstanceStartTime);

    ProcessReportEvaluationResultDto<RawDataProcessReportResultDto> result = createAndEvaluateReportWithRollingStartDateFilter(
        processInstance.getProcessDefinitionKey(),
        processInstance.getProcessDefinitionVersion(),
        "days",
        false
    );

    assertResults(processInstance, result, 1);

    //when
    LocalDateUtil.setCurrentTime(OffsetDateTime.now().plusDays(2L));

    //token has to be refreshed, as the old one expired already after moving the date
    result = createAndEvaluateReportWithRollingStartDateFilter(
        processInstance.getProcessDefinitionKey(),
        processInstance.getProcessDefinitionVersion(),
        "days",
        true
    );

    assertResults(processInstance, result, 0);
  }

  @Test
  public void testEndDateRollingLogic() {
    embeddedOptimizeRule.reloadConfiguration();
    ProcessInstanceEngineDto processInstance = deployAndStartSimpleProcess();

    engineRule.finishAllUserTasks(processInstance.getId());

    OffsetDateTime processInstanceEndTime =
            engineRule.getHistoricProcessInstance(processInstance.getId()).getEndTime();

    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    LocalDateUtil.setCurrentTime(processInstanceEndTime);

    //token has to be refreshed, as the old one expired already after moving the date
    ProcessReportEvaluationResultDto<RawDataProcessReportResultDto> result = createAndEvaluateReportWithRollingEndDateFilter(
            processInstance.getProcessDefinitionKey(),
            processInstance.getProcessDefinitionVersion(),
            "days",
            true
    );

    assertResults(processInstance, result, 1);

    LocalDateUtil.setCurrentTime(processInstanceEndTime.plusDays(2L));

    //token has to be refreshed, as the old one expired already after moving the date
    result = createAndEvaluateReportWithRollingEndDateFilter(
            processInstance.getProcessDefinitionKey(),
            processInstance.getProcessDefinitionVersion(),
            "days",
            true
    );

    assertResults(processInstance, result, 0);
  }
}
