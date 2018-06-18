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
package io.zeebe.model.bpmn.impl.transformation.nodes.task;

import static io.zeebe.msgpack.mapping.Mapping.JSON_ROOT_PATH;
import static io.zeebe.util.buffer.BufferUtil.bufferAsString;
import static io.zeebe.util.buffer.BufferUtil.wrapString;

import io.zeebe.model.bpmn.impl.error.ErrorCollector;
import io.zeebe.model.bpmn.impl.metadata.InputOutputMappingImpl;
import io.zeebe.model.bpmn.impl.metadata.MappingImpl;
import io.zeebe.model.bpmn.instance.OutputBehavior;
import io.zeebe.msgpack.jsonpath.JsonPathQuery;
import io.zeebe.msgpack.jsonpath.JsonPathQueryCompiler;
import io.zeebe.msgpack.mapping.Mapping;
import java.util.List;

public class InputOutputMappingTransformer {
  public void transform(ErrorCollector errorCollector, InputOutputMappingImpl inputOutputMapping) {
    final String outputBehaviorString = inputOutputMapping.getOutputBehaviorString();
    final OutputBehavior outputBehavior =
        OutputBehavior.valueOf(outputBehaviorString.toUpperCase());
    inputOutputMapping.setOutputBehavior(outputBehavior);

    final Mapping[] inputMappings = createMappings(errorCollector, inputOutputMapping.getInputs());
    inputOutputMapping.setInputMappings(inputMappings);

    final Mapping[] outputMappings =
        createMappings(errorCollector, inputOutputMapping.getOutputs());
    inputOutputMapping.setOutputMappings(outputMappings);
  }

  private Mapping[] createMappings(
      ErrorCollector errorCollector, final List<MappingImpl> mappings) {
    final Mapping[] map;

    if (mappings.size() == 1 && !isRootMapping(mappings.get(0))) {
      map = new Mapping[] {createMapping(errorCollector, mappings.get(0))};
    } else if (mappings.size() > 1) {
      map = new Mapping[mappings.size()];

      for (int i = 0; i < mappings.size(); i++) {
        map[i] = createMapping(errorCollector, mappings.get(i));
      }
    } else {
      map = new Mapping[0];
    }

    return map;
  }

  private boolean isRootMapping(MappingImpl mapping) {
    return mapping.getSource().equals(JSON_ROOT_PATH) && mapping.getTarget().equals(JSON_ROOT_PATH);
  }

  private Mapping createMapping(ErrorCollector errorCollector, MappingImpl mapping) {
    final JsonPathQueryCompiler queryCompiler = new JsonPathQueryCompiler();
    final JsonPathQuery query = queryCompiler.compile(mapping.getSource());

    if (!query.isValid()) {
      errorCollector.addError(
          mapping,
          String.format(
              "JSON path query '%s' is not valid! Reason: %s",
              bufferAsString(query.getExpression()), query.getErrorReason()));
    }

    return new Mapping(query, wrapString(mapping.getTarget()));
  }
}
