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
import io.zeebe.util.TriConsumer;
import io.zeebe.util.collection.Tuple;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.ToLongFunction;

public final class UpgradeTestCase {
  private TestCaseBuilder builder;

  private UpgradeTestCase(final TestCaseBuilder builder) {
    this.builder = builder;
  }

  static TestCaseBuilder builder() {
    return new TestCaseBuilder();
  }

  long setUp(final ZeebeClient client) {
    builder.deployWorkflow.accept(client);
    return builder.createInstance.applyAsLong(client);
  }

  Long runBefore(final ContainerStateRule state) {
    return builder.before.applyAsLong(state);
  }

  void runAfter(final ContainerStateRule state, final long wfInstanceKey, final long key) {
    builder.after.accept(state, wfInstanceKey, key);
  }

  static class TestCaseBuilder {
    private Consumer<ZeebeClient> deployWorkflow = c -> {};
    private ToLongFunction<ZeebeClient> createInstance = c -> -1L;
    private ToLongFunction<ContainerStateRule> before = r -> -1L;
    private TriConsumer<ContainerStateRule, Long, Long> after = (r, wfKey, k) -> {};

    TestCaseBuilder deployWorkflow(final BpmnModelInstance model) {
      return deployWorkflow(new Tuple<>(model, UpgradeTest.PROCESS_ID));
    }

    @SafeVarargs
    final TestCaseBuilder deployWorkflow(final Tuple<BpmnModelInstance, String>... models) {
      for (final Tuple<BpmnModelInstance, String> model : models) {
        deployWorkflow =
            deployWorkflow.andThen(
                client ->
                    client
                        .newDeployCommand()
                        .addWorkflowModel(model.getLeft(), model.getRight() + ".bpmn")
                        .send()
                        .join());
      }
      return this;
    }

    TestCaseBuilder createInstance() {
      return createInstance(Map.of());
    }

    TestCaseBuilder createInstance(final Map<String, Object> variables) {
      createInstance =
          client ->
              client
                  .newCreateInstanceCommand()
                  .bpmnProcessId(UpgradeTest.PROCESS_ID)
                  .latestVersion()
                  .variables(variables)
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
    TestCaseBuilder beforeUpgrade(final ToLongFunction<ContainerStateRule> func) {
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
}
