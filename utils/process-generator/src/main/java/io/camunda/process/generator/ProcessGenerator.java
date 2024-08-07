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

import io.camunda.process.generator.element.BpmnElementGeneratorFactory;
import io.camunda.process.generator.template.BpmnTemplateGenerator;
import io.camunda.process.generator.template.BpmnTemplateGeneratorFactory;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.model.bpmn.builder.AbstractFlowNodeBuilder;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

public class ProcessGenerator {

  private final BpmnElementGeneratorFactory elementGeneratorFactory;
  private final BpmnTemplateGeneratorFactory templateGeneratorFactory;

  public ProcessGenerator() {
    this(ThreadLocalRandom.current().nextLong());
  }

  public ProcessGenerator(final long seed) {
    final Random random = new Random(seed);
    final var generatorContent = new GeneratorContext(random);

    elementGeneratorFactory = new BpmnElementGeneratorFactory(generatorContent);
    templateGeneratorFactory =
        new BpmnTemplateGeneratorFactory(generatorContent, elementGeneratorFactory);
  }

  public BpmnModelInstance generateProcess() {
    AbstractFlowNodeBuilder<?, ?> processBuilder =
        Bpmn.createExecutableProcess("process").startEvent();

    final BpmnTemplateGenerator templateGenerator = templateGeneratorFactory.getGenerator();
    processBuilder = templateGenerator.addElements(processBuilder);

    return processBuilder.endEvent().done();
  }
}
