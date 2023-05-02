/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.test;

import static io.camunda.zeebe.test.UpdateTestCaseProvider.PROCESS_ID;

import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.util.collection.Tuple;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.ToLongFunction;
import org.junit.jupiter.params.provider.Arguments;

@SuppressWarnings("java:S2187") // false positive due to ending with *TestCase
final class UpdateTestCase implements Arguments {
  private final TestCaseBuilder builder;

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
    builder.deployProcess.accept(client);
    return builder.createInstance.applyAsLong(client);
  }

  Long runBefore(final ContainerState state) {
    return builder.before.applyAsLong(state);
  }

  void runAfter(final ContainerState state, final long processInstanceKey, final long key) {
    builder.after.accept(state, processInstanceKey, key);
  }

  static class TestCaseBuilder {
    private String name;
    private Consumer<ZeebeClient> deployProcess = c -> {};
    private ToLongFunction<ZeebeClient> createInstance = c -> -1L;
    private ToLongFunction<ContainerState> before = r -> -1L;
    private TriConsumer<ContainerState, Long, Long> after = (r, processKey, k) -> {};

    TestCaseBuilder name(final String name) {
      this.name = Objects.requireNonNull(name);
      return this;
    }

    TestCaseBuilder deployProcess(final BpmnModelInstance model) {
      return deployProcess(new Tuple<>(model, PROCESS_ID));
    }

    @SafeVarargs
    final TestCaseBuilder deployProcess(final Tuple<BpmnModelInstance, String>... models) {
      for (final Tuple<BpmnModelInstance, String> model : models) {
        deployProcess =
            deployProcess.andThen(
                client ->
                    client
                        .newDeployCommand()
                        .addProcessModel(model.getLeft(), model.getRight() + ".bpmn")
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
                  .getProcessInstanceKey();
      return this;
    }

    /**
     * Should make zeebe write records and write to state of the feature being tested (e.g., jobs,
     * messages). The process should be left in a waiting state so Zeebe can be restarted and
     * execution can be continued after. Takes the container rule as input and outputs a long which
     * can be used after the upgrade to continue the execution.
     */
    TestCaseBuilder beforeUpgrade(final ToLongFunction<ContainerState> func) {
      before = func;
      return this;
    }

    /**
     * Should continue the instance after the upgrade in a way that will complete the process. Takes
     * the container rule and a long (e.g., a key) as input.
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
