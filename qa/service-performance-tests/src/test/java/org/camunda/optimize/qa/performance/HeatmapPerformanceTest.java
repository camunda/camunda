package org.camunda.optimize.qa.performance;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.camunda.optimize.dto.optimize.query.report.filter.DateFilterDto;
import org.camunda.optimize.dto.optimize.query.report.filter.ExecutedFlowNodeFilterDto;
import org.camunda.optimize.dto.optimize.query.report.filter.FilterDto;
import org.camunda.optimize.dto.optimize.query.report.filter.VariableFilterDto;
import org.camunda.optimize.dto.optimize.query.report.filter.data.DateFilterDataDto;
import org.camunda.optimize.dto.optimize.query.report.filter.data.VariableFilterDataDto;
import org.camunda.optimize.dto.optimize.query.report.filter.util.ExecutedFlowNodeFilterBuilder;
import org.camunda.optimize.qa.performance.framework.PerfTest;
import org.camunda.optimize.qa.performance.framework.PerfTestResult;
import org.camunda.optimize.qa.performance.framework.PerfTestStepResult;
import org.camunda.optimize.qa.performance.steps.GetDurationGetHeatMapStep;
import org.camunda.optimize.qa.performance.steps.GetFrequencyGetHeatMapStep;
import org.camunda.optimize.qa.performance.steps.decorator.HeatMapDataGenerationStep;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import static org.camunda.optimize.service.es.filter.FilterOperatorConstants.IN;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.number.OrderingComparison.lessThan;

public class HeatmapPerformanceTest extends OptimizePerformanceTestCase {
  protected List<FilterDto> filter = new ArrayList<>();
  private PerfTest test;

  @Before
  public void setUp() throws JsonProcessingException {
    super.setUp();
    filter = new ArrayList<>();
    testBuilder = this.testBuilder
        .step(new HeatMapDataGenerationStep());
  }

  @Test
  public void getFrequencyHeatmapWithoutFilter() {
    //given
    test = testBuilder
        .step(new GetFrequencyGetHeatMapStep(filter))
        .done();

    // when
    PerfTestResult testResult = test.run();
    PerfTestStepResult stepResult =
      testResult.getResult(GetFrequencyGetHeatMapStep.class);

    // then
    assertThat(stepResult.getDurationInMs(), is(lessThan(configuration.getMaxServiceExecutionDuration())));
  }

  @Test
  public void getFrequencyHeatmapWithDateFilter() {
    // given
    String operator = "<";
    String type = "start_date";
    DateFilterDataDto date = new DateFilterDataDto();
    date.setOperator(operator);
    date.setType(type);
    date.setValue(new Date());

    DateFilterDto dateFilterDto = new DateFilterDto();
    dateFilterDto.setData(date);
    filter.add(dateFilterDto);

    test = testBuilder
      .step(new GetFrequencyGetHeatMapStep(filter))
      .done();

    // when
    PerfTestResult testResult = test.run();
    PerfTestStepResult stepResult =
      testResult.getResult(GetFrequencyGetHeatMapStep.class);

    // then
    assertThat(stepResult.getDurationInMs(), is(lessThan(configuration.getMaxServiceExecutionDuration())));
  }

  @Test
  public void getFrequencyHeatmapWithVariableFilter() {
    // given
    VariableFilterDataDto data = new VariableFilterDataDto();
    data.setName("var");
    data.setType("string");
    data.setOperator(IN);
    data.setValues(Collections.singletonList("aStringValue"));

    VariableFilterDto variableFilterDto = new VariableFilterDto();
    variableFilterDto.setData(data);
    filter.add(variableFilterDto);

    test = testBuilder
      .step(new GetFrequencyGetHeatMapStep(filter))
      .done();

    // when
    PerfTestResult testResult = test.run();
    PerfTestStepResult stepResult =
      testResult.getResult(GetFrequencyGetHeatMapStep.class);

    // then
    assertThat(stepResult.getDurationInMs(), is(lessThan(configuration.getMaxServiceExecutionDuration())));
  }

  @Test
  public void getFrequencyHeatmapWithExecutedFlowNodeFilter() {
    // given
    List<ExecutedFlowNodeFilterDto> executedFlowNodes =
      ExecutedFlowNodeFilterBuilder.construct()
        .id("startEvent")
        .build();
    filter.addAll(executedFlowNodes);

    test = testBuilder
      .step(new GetFrequencyGetHeatMapStep(filter))
      .done();

    // when
    PerfTestResult testResult = test.run();
    PerfTestStepResult stepResult =
      testResult.getResult(GetFrequencyGetHeatMapStep.class);

    // then
    assertThat(stepResult.getDurationInMs(), is(lessThan(configuration.getMaxServiceExecutionDuration())));
  }

  @Test
  public void getDurationHeatmapWithoutFilter() {
    //given
    test = testBuilder
        .step(new GetDurationGetHeatMapStep(filter))
        .done();

    // when
    PerfTestResult testResult = test.run();
    PerfTestStepResult stepResult =
      testResult.getResult(GetDurationGetHeatMapStep.class);

    // then
    assertThat(stepResult.getDurationInMs(), is(lessThan(configuration.getMaxServiceExecutionDuration())));
  }

  @Test
  public void getDurationHeatmapWithDateFilter() {
    // given
    String operator = "<";
    String type = "start_date";

    DateFilterDataDto date = new DateFilterDataDto();
    date.setOperator(operator);
    date.setType(type);
    date.setValue(new Date());

    DateFilterDto dateFilterDto = new DateFilterDto();
    dateFilterDto.setData(date);
    filter.add(dateFilterDto);

    test = testBuilder
        .step(new GetDurationGetHeatMapStep(filter))
        .done();

    // when
    PerfTestResult testResult = test.run();
    PerfTestStepResult stepResult =
      testResult.getResult(GetDurationGetHeatMapStep.class);

    // then
    assertThat(stepResult.getDurationInMs(), is(lessThan(configuration.getMaxServiceExecutionDuration())));
  }

  @Test
  public void getDurationHeatmapWithVariableFilter() {
    // given
    VariableFilterDataDto data = new VariableFilterDataDto();
    data.setName("var");
    data.setType("string");
    data.setOperator(IN);
    data.setValues(Collections.singletonList("aStringValue"));

    VariableFilterDto variableFilterDto = new VariableFilterDto();
    variableFilterDto.setData(data);
    filter.add(variableFilterDto);

    test = testBuilder
      .step(new GetDurationGetHeatMapStep(filter))
      .done();

    // when
    PerfTestResult testResult = test.run();
    PerfTestStepResult stepResult =
      testResult.getResult(GetDurationGetHeatMapStep.class);

    // then
    assertThat(stepResult.getDurationInMs(), is(lessThan(configuration.getMaxServiceExecutionDuration())));
  }

  @Test
  public void getDurationHeatmapWithExecutedFlowNodeFilter() {
    // given
    List<ExecutedFlowNodeFilterDto> executedFlowNodes =
      ExecutedFlowNodeFilterBuilder.construct()
        .id("startEvent")
        .build();
    filter.addAll(executedFlowNodes);

    test = testBuilder
      .step(new GetDurationGetHeatMapStep(filter))
      .done();

    // when
    PerfTestResult testResult = test.run();
    PerfTestStepResult stepResult =
      testResult.getResult(GetDurationGetHeatMapStep.class);

    // then
    assertThat(stepResult.getDurationInMs(), is(lessThan(configuration.getMaxServiceExecutionDuration())));
  }
}
