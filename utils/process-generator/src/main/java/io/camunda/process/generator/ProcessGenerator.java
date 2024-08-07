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

import io.camunda.process.generator.template.BpmnTemplateGenerator;
import io.camunda.process.generator.template.BpmnTemplateGeneratorFactory;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.model.bpmn.builder.AbstractFlowNodeBuilder;
import io.camunda.zeebe.model.bpmn.impl.BpmnModelConstants;
import io.camunda.zeebe.model.bpmn.instance.Definitions;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

public class ProcessGenerator {

  public static final String CAMUNDA_VERSION = "8.5.0";

  private final BpmnFactories factories;

  public ProcessGenerator() {
    this(ThreadLocalRandom.current().nextLong());
  }

  public ProcessGenerator(final long seed) {
    final Random random = new Random(seed);
    final var generatorContext = new GeneratorContext(random);

    factories = new BpmnFactories(generatorContext);
  }

  public BpmnModelInstance generateProcess() {
    AbstractFlowNodeBuilder<?, ?> processBuilder =
        Bpmn.createExecutableProcess("process").startEvent();

    final BpmnTemplateGeneratorFactory templateGeneratorFactory =
        factories.getTemplateGeneratorFactory();

    final var templateLimit = 3;
    for (int i = 0; i < templateLimit; i++) {
      final BpmnTemplateGenerator templateGenerator = templateGeneratorFactory.getGenerator();
      processBuilder = templateGenerator.addElements(processBuilder);
    }

    final BpmnModelInstance process = processBuilder.endEvent().done();

    // modify the version so I can open the process in the Camunda Modeler
    final Definitions definitions = process.getDefinitions();
    definitions.setExporterVersion(CAMUNDA_VERSION);
    definitions.setAttributeValueNs(
        BpmnModelConstants.MODELER_NS, "executionPlatformVersion", CAMUNDA_VERSION);

    return process;
  }
}
