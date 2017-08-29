package org.camunda.optimize.qa.performance;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.camunda.optimize.dto.optimize.query.DateFilterDto;
import org.camunda.optimize.dto.optimize.query.FilterMapDto;
import org.camunda.optimize.dto.optimize.query.flownode.ExecutedFlowNodeFilterBuilder;
import org.camunda.optimize.dto.optimize.query.flownode.ExecutedFlowNodeFilterDto;
import org.camunda.optimize.dto.optimize.query.variable.VariableFilterDto;
import org.camunda.optimize.qa.performance.framework.PerfTest;
import org.camunda.optimize.qa.performance.framework.PerfTestResult;
import org.camunda.optimize.qa.performance.framework.PerfTestStepResult;
import org.camunda.optimize.qa.performance.steps.BranchAnalysisDataGenerationStep;
import org.camunda.optimize.qa.performance.steps.GetBranchAnalysisStep;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.number.OrderingComparison.lessThan;

public class BranchAnalysisPerformanceTest extends OptimizePerformanceTestCase {

  private static final long CORRELATION_FACTOR = 10L;

  FilterMapDto filter = new FilterMapDto();
  PerfTest test;

  @Before
  public void setUp() throws JsonProcessingException {
    super.setUp();
    filter.setDates(new ArrayList<>());
    filter.setVariables(new ArrayList<>());
    filter.setExecutedFlowNodes(new ExecutedFlowNodeFilterDto());
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
  public void getBranchAnalysisWithDateFilter() {
    // given
    String operator = "<";
    String type = "start_date";

    DateFilterDto date = new DateFilterDto();
    date.setOperator(operator);
    date.setType(type);
    date.setValue(new Date());
    filter.getDates().add(date);

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
  public void getBranchAnalysisWithVariableFilter() {
    // given
    VariableFilterDto variableFilterDto = new VariableFilterDto();
    variableFilterDto.setName("var");
    variableFilterDto.setType("string");
    variableFilterDto.setOperator("=");
    variableFilterDto.setValues(Collections.singletonList("aStringValue"));
    filter.getVariables().add(variableFilterDto);

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
    ExecutedFlowNodeFilterDto flowNodeFilterDto =
      ExecutedFlowNodeFilterBuilder.construct()
        .id("startEvent")
        .build();
    filter.setExecutedFlowNodes(flowNodeFilterDto);

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
