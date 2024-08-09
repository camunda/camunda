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
package io.camunda.process.generator;

import io.camunda.process.generator.execution.ProcessExecutionStep;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public class BpmnProcessGenerator {

  public static final String CAMUNDA_VERSION = "8.5.0";

  public static GeneratorConfiguration DEFAULT_CONFIGURATION = new GeneratorConfiguration();
  private final GeneratorConfiguration generatorConfiguration;

  public BpmnProcessGenerator() {
    this(DEFAULT_CONFIGURATION);
  }

  public BpmnProcessGenerator(final GeneratorConfiguration generatorConfiguration) {
    this.generatorConfiguration = generatorConfiguration;
  }

  public GeneratedProcess generateProcess() {
    return generateProcess(ThreadLocalRandom.current().nextLong());
  }

  public GeneratedProcess generateProcess(final long seed) {
    final var generatorContext = new GeneratorContext(seed, generatorConfiguration);
    final var factories = new BpmnFactories(generatorContext);
    final ProcessGenerator processGenerator = new ProcessGenerator(CAMUNDA_VERSION, factories);
    return processGenerator.generateProcess(generatorContext);
  }

  public record GeneratedProcess(
      BpmnModelInstance process,
      List<ProcessExecutionStep> executionPath,
      String processId,
      long seed) {

    @Override
    public String toString() {
      return "GeneratedProcess{seed='%d'}".formatted(seed);
    }
  }

  public class Builder {}
}
