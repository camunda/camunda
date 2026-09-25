/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.microbenchmarks.deployment;

import io.camunda.zeebe.el.ExpressionLanguageFactory;
import io.camunda.zeebe.el.ExpressionLanguageMetrics;
import io.camunda.zeebe.engine.processing.bpmn.clock.ZeebeFeelEngineClock;
import io.camunda.zeebe.engine.processing.common.ExpressionProcessor;
import io.camunda.zeebe.engine.processing.deployment.model.BpmnFactory;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableProcess;
import io.camunda.zeebe.engine.processing.deployment.model.transformation.BpmnTransformer;
import io.camunda.zeebe.engine.processing.deployment.transform.BpmnValidator;
import io.camunda.zeebe.engine.processing.deployment.transform.ValidationConfig;
import io.camunda.zeebe.engine.processing.expression.ScopedEvaluationContext;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.model.bpmn.builder.AbstractFlowNodeBuilder;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.InstantSource;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.OptionsBuilder;

/**
 * Benchmarks the CPU-bound part of deploying a large BPMN process, i.e. what the deployment
 * processor does for a BPMN resource before writing any state: parse the XML, validate the model
 * and transform it into executable processes (see {@code BpmnResourceTransformer#createMetadata}).
 * State changes and record writing are left out, so everything runs on the benchmark thread.
 *
 * <p>{@link #measureParse()} measures the XML parsing alone, {@link #measureDeployment()} the full
 * parse, validate and transform pipeline. Comparing both tells how much time is spent in parsing.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 10, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 10, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(
    value = 1,
    jvmArgsAppend = {"-Xms4G", "-Xmx4G", "--add-opens=java.base/jdk.internal.misc=ALL-UNNAMED"})
@State(Scope.Benchmark)
public class LargeProcessDeploymentBenchmark {

  @Param({"100", "1000"})
  private int serviceTaskCount;

  private byte[] resource;
  private BpmnValidator validator;
  private BpmnTransformer transformer;

  public static void main(final String[] args) throws RunnerException {
    final var options =
        new OptionsBuilder()
            .addProfiler("gc")
            .include(LargeProcessDeploymentBenchmark.class.getSimpleName())
            .build();
    new Runner(options).run();
  }

  @Setup
  public void setup() {
    resource =
        Bpmn.convertToString(createLargeProcess(serviceTaskCount)).getBytes(StandardCharsets.UTF_8);

    final var clock = InstantSource.system();
    final var metrics = ExpressionLanguageMetrics.noop();
    final var expressionProcessor =
        new ExpressionProcessor(
            ExpressionLanguageFactory.createExpressionLanguage(
                new ZeebeFeelEngineClock(clock), metrics),
            ScopedEvaluationContext.NONE_INSTANCE,
            Duration.ofSeconds(1));
    validator =
        BpmnFactory.createValidator(
            clock, expressionProcessor, ValidationConfig.builder().build(), metrics);
    transformer = BpmnFactory.createTransformer(clock, metrics);
  }

  @Benchmark
  public BpmnModelInstance measureParse() {
    return parse();
  }

  @Benchmark
  public List<ExecutableProcess> measureDeployment() {
    final var model = parse();
    final var validationError = validator.validate(model);
    if (validationError != null) {
      throw new IllegalStateException(validationError);
    }
    return transformer.transformDefinitions(model);
  }

  private BpmnModelInstance parse() {
    return Bpmn.readModelFromStream(new ByteArrayInputStream(resource));
  }

  /**
   * Creates a sequential process of service tasks, each with FEEL input/output mappings and a job
   * type expression, since expressions are parsed and validated on deployment too.
   */
  private static BpmnModelInstance createLargeProcess(final int serviceTaskCount) {
    AbstractFlowNodeBuilder<?, ?> builder =
        Bpmn.createExecutableProcess("large-process").startEvent();
    for (int i = 0; i < serviceTaskCount; i++) {
      final var index = i;
      builder =
          builder.serviceTask(
              "task-" + i,
              t ->
                  t.zeebeJobTypeExpression("\"task-\" + string(" + index + ")")
                      .zeebeInputExpression("order.items[" + (index + 1) + "]", "item")
                      .zeebeInputExpression("if item.price > 100 then true else false", "premium")
                      .zeebeOutputExpression("result.status", "status" + index));
    }
    return builder.endEvent().done();
  }
}
