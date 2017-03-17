package org.camunda.optimize.qa.performance.heatmap;

import org.camunda.optimize.dto.optimize.DateFilterDto;
import org.camunda.optimize.dto.optimize.FilterMapDto;
import org.camunda.optimize.qa.performance.OptimizePerformanceTestCase;
import org.camunda.optimize.qa.performance.framework.PerfTest;
import org.camunda.optimize.qa.performance.framework.PerfTestResult;
import org.camunda.optimize.qa.performance.framework.PerfTestStepResult;
import org.camunda.optimize.qa.performance.steps.DataGenerationStep;
import org.camunda.optimize.qa.performance.steps.GetDurationHeatMapStep;
import org.camunda.optimize.qa.performance.steps.GetFrequencyHeatMapStep;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Date;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.number.OrderingComparison.lessThan;

public class HeatmapPerformanceTest extends OptimizePerformanceTestCase {

  @Test
  public void getFrequencyHeatmapWithoutFilter() {
    // given
    FilterMapDto filter;
    filter = new FilterMapDto();
    filter.setDates(new ArrayList<>());

    PerfTest test =
      createPerformanceTest()
        .step(new DataGenerationStep())
        .step(new GetFrequencyHeatMapStep(filter))
        .done();

    // when
    PerfTestResult testResult = test.run();
    PerfTestStepResult stepResult =
      testResult.getResult(GetFrequencyHeatMapStep.class);

    // then
    assertThat(stepResult.getDurationInMs(), is(lessThan(configuration.getMaxGetHeatMapDuration())));
  }

  @Test
  public void getFrequencyHeatmapWithFilters() {
    // given
    String operator = "<";
    String type = "start_date";
    FilterMapDto filter;
    filter = new FilterMapDto();
    filter.setDates(new ArrayList<>());

    DateFilterDto date = new DateFilterDto();
    date.setOperator(operator);
    date.setType(type);
    date.setValue(new Date());
    filter.getDates().add(date);

    PerfTest test =
      createPerformanceTest()
        .step(new DataGenerationStep())
        .step(new GetFrequencyHeatMapStep(filter))
        .done();

    // when
    PerfTestResult testResult = test.run();
    PerfTestStepResult stepResult =
      testResult.getResult(GetFrequencyHeatMapStep.class);

    // then
    assertThat(stepResult.getDurationInMs(), is(lessThan(configuration.getMaxGetHeatMapDuration())));
  }

  @Test
  public void getDurationHeatmapWithoutFilter() {
    // given
    FilterMapDto filter;
    filter = new FilterMapDto();
    filter.setDates(new ArrayList<>());

    PerfTest test =
      createPerformanceTest()
        .step(new DataGenerationStep())
        .step(new GetDurationHeatMapStep(filter))
        .done();

    // when
    PerfTestResult testResult = test.run();
    PerfTestStepResult stepResult =
      testResult.getResult(GetDurationHeatMapStep.class);

    // then
    assertThat(stepResult.getDurationInMs(), is(lessThan(configuration.getMaxGetHeatMapDuration())));
  }

  @Test
  public void getDurationHeatmapWithFilters() {
    // given
    String operator = "<";
    String type = "start_date";
    FilterMapDto filter;
    filter = new FilterMapDto();
    filter.setDates(new ArrayList<>());

    DateFilterDto date = new DateFilterDto();
    date.setOperator(operator);
    date.setType(type);
    date.setValue(new Date());
    filter.getDates().add(date);

    PerfTest test =
      createPerformanceTest()
        .step(new DataGenerationStep())
        .step(new GetDurationHeatMapStep(filter))
        .done();

    // when
    PerfTestResult testResult = test.run();
    PerfTestStepResult stepResult =
      testResult.getResult(GetDurationHeatMapStep.class);

    // then
    assertThat(stepResult.getDurationInMs(), is(lessThan(configuration.getMaxGetHeatMapDuration())));
  }
}
