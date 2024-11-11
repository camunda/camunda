/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service.processtest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import io.camunda.service.processtest.actions.CompleteJobAction;
import io.camunda.service.processtest.actions.CreateProcessInstanceAction;
import io.camunda.service.processtest.dsl.TestCase;
import io.camunda.service.processtest.dsl.TestResource;
import io.camunda.service.processtest.dsl.TestSpecification;
import io.camunda.service.processtest.verifications.ProcessInstanceCompletedVerification;
import io.camunda.zeebe.model.bpmn.Bpmn;
import java.util.List;
import org.junit.jupiter.api.Test;

public class ProcessTestRunnerTest {

  @Test
  void shouldRunTest() {
    // given
    final ProcessTestRunner processTestRunner = new ProcessTestRunner();

    final var process =
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .serviceTask("A", task -> task.zeebeJobType("A"))
            .endEvent()
            .done();

    final List<TestResource> testResources =
        List.of(new TestResource("process.bpmn", Bpmn.convertToString(process)));

    final var testCase1 =
        new TestCase(
            "case-1",
            List.of(
                new CreateProcessInstanceAction("process", "{}", "process-instance"),
                new CompleteJobAction("A", "{}"),
                new ProcessInstanceCompletedVerification("process-instance")));

    final var testCase2 =
        new TestCase(
            "case-2",
            List.of(
                new CreateProcessInstanceAction("process", "{}", "process-instance"),
                new ProcessInstanceCompletedVerification("process-instance")));

    final var testSpecification =
        new TestSpecification(testResources, List.of(testCase1, testCase2));

    // when
    final TestSpecificationResult result = processTestRunner.run(testSpecification);

    // then
    assertThat(result.testResults())
        .hasSize(2)
        .extracting(TestCaseResult::success, TestCaseResult::failureMessage)
        .containsSequence(
            tuple(true, "<success>"),
            tuple(
                false,
                "Process instance [key: 2251799813685251] should be completed but was active."));
  }
}
