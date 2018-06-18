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
package io.zeebe.model.bpmn;

import static java.nio.charset.StandardCharsets.UTF_8;

import io.zeebe.model.bpmn.builder.BpmnBuilder;
import io.zeebe.model.bpmn.impl.BpmnParser;
import io.zeebe.model.bpmn.impl.instance.DefinitionsImpl;
import io.zeebe.model.bpmn.impl.transformation.BpmnTransformer;
import io.zeebe.model.bpmn.impl.validation.BpmnValidator;
import io.zeebe.model.bpmn.impl.yaml.BpmnYamlParser;
import io.zeebe.model.bpmn.instance.WorkflowDefinition;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import org.agrona.DirectBuffer;

public class BpmnModelApi {
  private final BpmnParser parser = new BpmnParser();
  private final BpmnTransformer transformer = new BpmnTransformer();
  private final BpmnValidator validator = new BpmnValidator();
  private final BpmnBuilder builder = new BpmnBuilder(transformer, validator);
  private final BpmnYamlParser yamlParser = new BpmnYamlParser(builder);

  public BpmnBuilder createExecutableWorkflow(String bpmnProcessId) {
    return builder.wrap(bpmnProcessId);
  }

  public WorkflowDefinition readFromXmlFile(File file) {
    // lexer and parser
    final DefinitionsImpl definitions = parser.readFromFile(file);

    // semantic analyzer
    validator.validate(definitions);

    // generator/transformer
    return transformer.transform(definitions);
  }

  public WorkflowDefinition readFromXmlStream(InputStream stream) {
    final DefinitionsImpl definitions = parser.readFromStream(stream);

    // semantic analyzer
    validator.validate(definitions);

    // generator/transformer
    return transformer.transform(definitions);
  }

  public WorkflowDefinition readFromXmlBuffer(DirectBuffer buffer) {
    final byte[] bytes = new byte[buffer.capacity()];
    buffer.getBytes(0, bytes);

    return readFromXmlStream(new ByteArrayInputStream(bytes));
  }

  public WorkflowDefinition readFromXmlString(String workflow) {
    return readFromXmlStream(new ByteArrayInputStream(workflow.getBytes(UTF_8)));
  }

  public WorkflowDefinition readFromYamlFile(File file) {
    return yamlParser.readFromFile(file);
  }

  public WorkflowDefinition readFromYamlStream(InputStream stream) {
    return yamlParser.readFromStream(stream);
  }

  public WorkflowDefinition readFromYamlBuffer(DirectBuffer buffer) {
    final byte[] bytes = new byte[buffer.capacity()];
    buffer.getBytes(0, bytes);

    return yamlParser.readFromStream(new ByteArrayInputStream(bytes));
  }

  public WorkflowDefinition readFromYamlString(String workflow) {
    return yamlParser.readFromStream(new ByteArrayInputStream(workflow.getBytes(UTF_8)));
  }

  public String convertToString(WorkflowDefinition definition) {
    if (definition instanceof DefinitionsImpl) {
      return parser.convertToString((DefinitionsImpl) definition);
    } else {
      throw new RuntimeException("not supported");
    }
  }
}
