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

import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

public class BpmnGenerator {

  public static final String CAMUNDA_VERSION = "8.5.0";

  private final ProcessGenerator processGenerator;

  public BpmnGenerator() {
    this(ThreadLocalRandom.current().nextLong());
  }

  public BpmnGenerator(final long seed) {
    final Random random = new Random(seed);
    final var generatorContext = new GeneratorContext(random);
    final var factories = new BpmnFactories(generatorContext);
    processGenerator = new ProcessGenerator(CAMUNDA_VERSION, generatorContext, factories);
  }

  public BpmnModelInstance generateProcess() {
    return processGenerator.generateProcess();
  }
}
