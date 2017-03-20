package org.camunda.optimize.qa.performance;

import org.camunda.optimize.dto.optimize.DateFilterDto;
import org.camunda.optimize.dto.optimize.FilterMapDto;
import org.camunda.optimize.qa.performance.framework.PerfTest;
import org.camunda.optimize.qa.performance.framework.PerfTestResult;
import org.camunda.optimize.qa.performance.framework.PerfTestStepResult;
import org.camunda.optimize.qa.performance.steps.CorrelationDiscoveryDataGenerationStep;
import org.camunda.optimize.qa.performance.steps.GetActivityCorrelationStep;
import org.camunda.optimize.qa.performance.steps.GetFrequencyGetHeatMapStep;
import org.camunda.optimize.qa.performance.steps.decorator.HeatMapDataGenerationStep;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Date;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.number.OrderingComparison.lessThan;

public class ActivityCorrelationPerformanceTest extends OptimizePerformanceTestCase {

  @Test
  public void getActivityCorrelationWithoutFilter() {
    // given
    FilterMapDto filter;
    filter = new FilterMapDto();
    filter.setDates(new ArrayList<>());

    PerfTest test =
      createPerformanceTest()
        .step(new CorrelationDiscoveryDataGenerationStep())
        .step(new GetActivityCorrelationStep(filter))
        .done();

    // when
    PerfTestResult testResult = test.run();
    PerfTestStepResult stepResult =
      testResult.getResult(GetActivityCorrelationStep.class);

    // then
    assertThat(stepResult.getDurationInMs(), is(lessThan(configuration.getMaxServiceExecutionDuration() * 4)));
  }

  @Test
  public void getActivityCorrelationWithFilters() {
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
        .step(new CorrelationDiscoveryDataGenerationStep())
        .step(new GetActivityCorrelationStep(filter))
        .done();

    // when
    PerfTestResult testResult = test.run();
    PerfTestStepResult stepResult =
      testResult.getResult(GetActivityCorrelationStep.class);

    // then
    assertThat(stepResult.getDurationInMs(), is(lessThan(configuration.getMaxServiceExecutionDuration() * 4)));
  }

}
