/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.test;

import static io.zeebe.test.UpdateTestCaseProvider.PROCESS_ID;

import io.zeebe.client.ZeebeClient;
import io.zeebe.model.bpmn.BpmnModelInstance;
import io.zeebe.util.collection.Tuple;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.ToLongFunction;
import org.junit.jupiter.params.provider.Arguments;

@SuppressWarnings("java:S2187") // false positive due to ending with *TestCase
final class UpdateTestCase implements Arguments {
  private TestCaseBuilder builder;

  private UpdateTestCase(final TestCaseBuilder builder) {
    this.builder = builder;
    Objects.requireNonNull(builder.name);
  }

  @Override
  public Object[] get() {
    return new Object[] {builder.name, this};
  }

  static TestCaseBuilder builder() {
    return new TestCaseBuilder();
  }

  long setUp(final ZeebeClient client) {
    builder.deployWorkflow.accept(client);
    return builder.createInstance.applyAsLong(client);
  }

  Long runBefore(final ContainerState state) {
    return builder.before.applyAsLong(state);
  }

  void runAfter(final ContainerState state, final long wfInstanceKey, final long key) {
    builder.after.accept(state, wfInstanceKey, key);
  }

  static class TestCaseBuilder {
    private String name;
    private Consumer<ZeebeClient> deployWorkflow = c -> {};
    private ToLongFunction<ZeebeClient> createInstance = c -> -1L;
    private ToLongFunction<ContainerState> before = r -> -1L;
    private TriConsumer<ContainerState, Long, Long> after = (r, wfKey, k) -> {};

    TestCaseBuilder name(final String name) {
      this.name = Objects.requireNonNull(name);
      return this;
    }

    TestCaseBuilder deployWorkflow(final BpmnModelInstance model) {
      return deployWorkflow(new Tuple<>(model, PROCESS_ID));
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
                  .bpmnProcessId(PROCESS_ID)
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
    TestCaseBuilder beforeUpgrade(final ToLongFunction<ContainerState> func) {
      before = func;
      return this;
    }
    /**
     * Should continue the instance after the upgrade in a way that will complete the workflow.
     * Takes the container rule and a long (e.g., a key) as input.
     */
    TestCaseBuilder afterUpgrade(final TriConsumer<ContainerState, Long, Long> func) {
      after = func;
      return this;
    }

    UpdateTestCase done() {
      return new UpdateTestCase(this);
    }
  }

  @FunctionalInterface
  interface TriConsumer<A, B, C> {
    void accept(A a, B b, C c);
  }
}
