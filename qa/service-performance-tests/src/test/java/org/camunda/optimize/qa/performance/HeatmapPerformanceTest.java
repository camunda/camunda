package org.camunda.optimize.qa.performance;

import org.camunda.optimize.dto.optimize.DateFilterDto;
import org.camunda.optimize.dto.optimize.FilterMapDto;
import org.camunda.optimize.qa.performance.framework.PerfTest;
import org.camunda.optimize.qa.performance.framework.PerfTestResult;
import org.camunda.optimize.qa.performance.framework.PerfTestStepResult;
import org.camunda.optimize.qa.performance.steps.GetDurationGetHeatMapStep;
import org.camunda.optimize.qa.performance.steps.GetFrequencyGetHeatMapStep;
import org.camunda.optimize.qa.performance.steps.decorator.HeatMapDataGenerationStep;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Date;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.number.OrderingComparison.lessThan;

public class HeatmapPerformanceTest extends OptimizePerformanceTestCase {
  FilterMapDto filter = new FilterMapDto();
  PerfTest test;

  @Before
  public void setUp() {
    super.setUp();
    filter.setDates(new ArrayList<>());
    filter.setVariables(new ArrayList<>());
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
  public void getFrequencyHeatmapWithFilters() {
    // given
    String operator = "<";
    String type = "start_date";
    DateFilterDto date = new DateFilterDto();
    date.setOperator(operator);
    date.setType(type);
    date.setValue(new Date());

    filter.getDates().add(date);

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
  public void getDurationHeatmapWithFilters() {
    // given
    String operator = "<";
    String type = "start_date";

    DateFilterDto date = new DateFilterDto();
    date.setOperator(operator);
    date.setType(type);
    date.setValue(new Date());
    filter.getDates().add(date);

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
