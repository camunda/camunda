/*
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.camunda.process.test.impl.coverage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.search.filter.DecisionInstanceFilter;
import io.camunda.client.api.search.filter.ProcessInstanceFilter;
import io.camunda.client.api.search.page.AnyPage;
import io.camunda.client.api.search.sort.ProcessInstanceSort;
import io.camunda.process.test.api.coverage.CoverageCollector;
import io.camunda.process.test.api.coverage.CoverageTestIdentifier;
import io.camunda.process.test.api.coverage.model.CoverageReport;
import java.time.Instant;
import java.util.Collections;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CoverageCollectorImplTest {

  @Mock(answer = Answers.RETURNS_DEEP_STUBS)
  private CamundaClient client;

  private final CoverageCollector collector = CoverageCollector.newBuilder().build();

  @BeforeEach
  void mockEmptyCluster() {
    when(client
            .newProcessInstanceSearchRequest()
            .filter(ArgumentMatchers.<Consumer<ProcessInstanceFilter>>any())
            .sort(ArgumentMatchers.<Consumer<ProcessInstanceSort>>any())
            .page(ArgumentMatchers.<Consumer<AnyPage>>any())
            .send()
            .join()
            .items())
        .thenReturn(Collections.emptyList());
    when(client
            .newDecisionInstanceSearchRequest()
            .filter(ArgumentMatchers.<Consumer<DecisionInstanceFilter>>any())
            .page(ArgumentMatchers.<Consumer<AnyPage>>any())
            .send()
            .join()
            .items())
        .thenReturn(Collections.emptyList());
  }

  @Test
  void shouldUseEnclosingSuiteNameAndKeepNestedRunNameForNestedTests() {
    // given
    final CoverageTestIdentifier testRun =
        CoverageTestIdentifier.of(
            NestedSuiteFixture.NestedSuiteTest.class, "NestedSuiteTest#run-1", null);

    // when
    final CoverageReport report = collector.collectTestRunCoverage(testRun, client, Instant.now());

    // then
    assertThat(report.getSuites())
        .singleElement()
        .satisfies(
            suite -> {
              assertThat(suite.getId()).isEqualTo(NestedSuiteFixture.class.getName());
              assertThat(suite.getName()).isEqualTo("NestedSuiteFixture");
              assertThat(suite.getRuns())
                  .singleElement()
                  .satisfies(run -> assertThat(run.getName()).isEqualTo("NestedSuiteTest#run-1"));
            });
  }

  @Test
  void shouldCombineNestedSuitesOfSameEnclosingClassInSingleReport() {
    // given
    final Instant startTime = Instant.now();

    // when
    collector.collectTestRunCoverage(
        CoverageTestIdentifier.of(
            NestedCollectorFixture.SecondNestedSuiteTest.class,
            "SecondNestedSuiteTest#run-2",
            null),
        client,
        startTime);
    final CoverageReport report =
        collector.collectTestRunCoverage(
            CoverageTestIdentifier.of(
                NestedCollectorFixture.FirstNestedSuiteTest.class,
                "FirstNestedSuiteTest#run-1",
                null),
            client,
            startTime);

    // then
    assertThat(report.getSuites())
        .singleElement()
        .satisfies(
            suite -> {
              assertThat(suite.getName()).isEqualTo("NestedCollectorFixture");
              assertThat(suite.getRuns())
                  .extracting(run -> run.getName())
                  .containsExactlyInAnyOrder(
                      "SecondNestedSuiteTest#run-2", "FirstNestedSuiteTest#run-1");
            });
  }

  @Test
  void shouldReturnAggregatedReportOfAllSuites() {
    // given
    final Instant startTime = Instant.now();
    collector.collectTestRunCoverage(
        CoverageTestIdentifier.of("suite-a", "Suite A", "run-1", null), client, startTime);
    collector.collectTestRunCoverage(
        CoverageTestIdentifier.of("suite-b", "Suite B", "run-1", null), client, startTime);

    // when
    final CoverageReport report = collector.getAggregatedReport();

    // then
    assertThat(report.getSuites())
        .extracting(suite -> suite.getId())
        .containsExactlyInAnyOrder("suite-a", "suite-b");
  }
}

final class NestedSuiteFixture {
  private NestedSuiteFixture() {}

  static final class NestedSuiteTest {}
}

final class NestedCollectorFixture {
  private NestedCollectorFixture() {}

  static final class FirstNestedSuiteTest {}

  static final class SecondNestedSuiteTest {}
}
