package org.camunda.optimize.service.es.filter.decision;

import com.google.common.collect.Lists;
import org.camunda.optimize.dto.engine.DecisionDefinitionEngineDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.DecisionReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.result.raw.RawDataDecisionReportResultDto;
import org.camunda.optimize.service.es.report.decision.AbstractDecisionDefinitionIT;
import org.camunda.optimize.test.util.DecisionReportDataBuilder;
import org.junit.Test;

import static org.camunda.optimize.dto.optimize.ReportConstants.ALL_VERSIONS;
import static org.camunda.optimize.test.util.DecisionFilterUtilHelper.createBooleanOutputVariableFilter;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.IsNull.notNullValue;

public class DecisionBooleanVariableFilterIT extends AbstractDecisionDefinitionIT {

  @Test
  public void resultFilterByEqualBooleanOutputVariable() {
    // given
    final String outputAuditValueToFilterFor = "true";
    final String outputVariableIdToFilterOn = OUTPUT_AUDIT_ID;

    final DecisionDefinitionEngineDto decisionDefinitionDto = engineRule.deployDecisionDefinition();
    // results in Audit=false
    startDecisionInstanceWithInputVars(
      decisionDefinitionDto.getId(),
      createInputs(100.0, "Misc")
    );
    // results in Audit=true
    startDecisionInstanceWithInputVars(
      decisionDefinitionDto.getId(),
      createInputs(2000.0, "Misc")
    );

    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    DecisionReportDataDto reportData = DecisionReportDataBuilder.createDecisionReportDataViewRawAsTable(
      decisionDefinitionDto.getKey(), ALL_VERSIONS
    );
    reportData.setFilter(Lists.newArrayList(createBooleanOutputVariableFilter(
      outputVariableIdToFilterOn, outputAuditValueToFilterFor
    )));
    RawDataDecisionReportResultDto result = evaluateRawReport(reportData).getResult();

    // then
    assertThat(result.getDecisionInstanceCount(), is(1L));
    assertThat(result.getData(), is(notNullValue()));
    assertThat(result.getData().size(), is(1));

    assertThat(
      result.getData().get(0).getOutputVariables().get(outputVariableIdToFilterOn).getFirstValue(),
      is(outputAuditValueToFilterFor)
    );
  }

}
