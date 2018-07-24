package org.camunda.optimize.qa.performance;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.camunda.optimize.dto.optimize.query.report.filter.ExecutedFlowNodeFilterDto;
import org.camunda.optimize.dto.optimize.query.report.filter.FilterDto;
import org.camunda.optimize.dto.optimize.query.report.filter.VariableFilterDto;
import org.camunda.optimize.dto.optimize.query.report.filter.data.variable.StringVariableFilterDataDto;
import org.camunda.optimize.dto.optimize.query.report.filter.util.ExecutedFlowNodeFilterBuilder;
import org.camunda.optimize.qa.performance.framework.PerfTest;
import org.camunda.optimize.qa.performance.framework.PerfTestResult;
import org.camunda.optimize.qa.performance.framework.PerfTestStepResult;
import org.camunda.optimize.qa.performance.steps.BranchAnalysisDataGenerationStep;
import org.camunda.optimize.qa.performance.steps.GetBranchAnalysisStep;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.camunda.optimize.service.es.filter.FilterOperatorConstants.IN;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.number.OrderingComparison.lessThan;

public class BranchAnalysisPerformanceTest extends OptimizePerformanceTestCase {

  private static final long CORRELATION_FACTOR = 10L;

  protected List<FilterDto> filter = new ArrayList<>();
  PerfTest test;

  @Before
  public void setUp() throws JsonProcessingException {
    super.setUp();
    filter = new ArrayList<>();
    test = this.testBuilder
        .step(new BranchAnalysisDataGenerationStep())
        .step(new GetBranchAnalysisStep(filter))
        .done();
  }


  @Test
  public void getBranchAnalysisWithoutFilter() {

    // when
    PerfTestResult testResult = test.run();
    PerfTestStepResult stepResult =
      testResult.getResult(GetBranchAnalysisStep.class);

    // then
    assertThat(stepResult.getDurationInMs(), is(lessThan(configuration.getMaxServiceExecutionDuration() * CORRELATION_FACTOR)));
  }

  @Test
  public void getBranchAnalysisWithVariableFilter() {
    // given
    StringVariableFilterDataDto data = new StringVariableFilterDataDto(IN, Collections.singletonList("aStringValue"));
    data.setName("var");

    VariableFilterDto variableFilterDto = new VariableFilterDto();
    variableFilterDto.setData(data);
    filter.add(variableFilterDto);

    // when
    PerfTestResult testResult = test.run();
    PerfTestStepResult stepResult =
      testResult.getResult(GetBranchAnalysisStep.class);

    // then
    assertThat(
      stepResult.getDurationInMs(),
      is(lessThan(configuration.getMaxServiceExecutionDuration() * CORRELATION_FACTOR))
    );
  }

  @Test
  public void getBranchAnalysisWithExecutedFlowNodeFilter() {
    // given
    List<ExecutedFlowNodeFilterDto> executedFlowNodes =
      ExecutedFlowNodeFilterBuilder.construct()
        .id("startEvent")
        .build();
    filter.addAll(executedFlowNodes);

    // when
    PerfTestResult testResult = test.run();
    PerfTestStepResult stepResult =
      testResult.getResult(GetBranchAnalysisStep.class);

    // then
    assertThat(
      stepResult.getDurationInMs(),
      is(lessThan(configuration.getMaxServiceExecutionDuration() * CORRELATION_FACTOR))
    );
  }

}
