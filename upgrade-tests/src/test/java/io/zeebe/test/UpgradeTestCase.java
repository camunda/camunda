/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.test;

import io.zeebe.client.ZeebeClient;
import io.zeebe.model.bpmn.BpmnModelInstance;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;

final class UpgradeTestCase {
  private TestCaseBuilder builder;

  private UpgradeTestCase(final TestCaseBuilder builder) {
    this.builder = builder;
  }

  static TestCaseBuilder builder() {
    return new TestCaseBuilder();
  }

  long setUp(final ZeebeClient client) {
    builder.deployWorkflow.accept(client);

    if (builder.createInstance == null) {
      return -1;
    }

    return builder.createInstance.apply(client);
  }

  Long runBefore(final ContainerStateRule state) {
    return builder.before.apply(state);
  }

  void runAfter(final ContainerStateRule state, final long wfInstanceKey, final long key) {
    builder.after.accept(state, wfInstanceKey, key);
  }

  static class TestCaseBuilder {
    private Consumer<ZeebeClient> deployWorkflow;
    private Function<ZeebeClient, Long> createInstance;
    private Function<ContainerStateRule, Long> before;
    private TriConsumer<ContainerStateRule, Long, Long> after;

    TestCaseBuilder deployWorkflow(final BpmnModelInstance model) {
      deployWorkflow =
          client ->
              client
                  .newDeployCommand()
                  .addWorkflowModel(model, UpgradeTest.PROCESS_ID + ".bpmn")
                  .send()
                  .join();
      return this;
    }

    TestCaseBuilder createInstance() {
      createInstance =
          client ->
              client
                  .newCreateInstanceCommand()
                  .bpmnProcessId(UpgradeTest.PROCESS_ID)
                  .latestVersion()
                  .variables(Map.of("key", "123"))
                  .send()
                  .join()
                  .getWorkflowInstanceKey();
      return this;
    }
    /**
     * Should make zeebe write records and write to state of the feature being tested (e.g., jobs,
     * messages). The workflow should be left in a waiting state so Zeebe can be restarted and
     * execution can be continued after. Takes the container rule as input and outputs a long which
     * can be used after the upgrade to continue the execution.
     */
    TestCaseBuilder beforeUpgrade(final Function<ContainerStateRule, Long> func) {
      before = func;
      return this;
    }
    /**
     * Should continue the instance after the upgrade in a way that will complete the workflow.
     * Takes the container rule and a long (e.g., a key) as input.
     */
    TestCaseBuilder afterUpgrade(final TriConsumer<ContainerStateRule, Long, Long> func) {
      after = func;
      return this;
    }

    UpgradeTestCase done() {
      return new UpgradeTestCase(this);
    }
  }

  @FunctionalInterface
  interface TriConsumer<A, B, C> {
    void accept(A a, B b, C c);
  }
}
