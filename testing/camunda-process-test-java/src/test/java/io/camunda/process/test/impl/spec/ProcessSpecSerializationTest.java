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
package io.camunda.process.test.impl.spec;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.process.test.impl.spec.dsl.ProcessSpec;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import org.junit.jupiter.api.Test;

public class ProcessSpecSerializationTest {

  private final ObjectMapper objectMapper =
      new ObjectMapper().enable(MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS);

  private ProcessSpec parse(final InputStream testSpecification) throws IOException {
    return objectMapper.readValue(testSpecification, ProcessSpec.class);
  }

  @Test
  void shouldDeserializeBpmn() throws IOException {
    // given
    final InputStream serializedProcessSpec =
        getClass().getResourceAsStream("/specs/test-spec-1.spec");
    assertThat(serializedProcessSpec).describedAs("Test resource not found").isNotNull();

    // when
    final ProcessSpec processSpec = parse(serializedProcessSpec);

    // then
    assertThat(processSpec).isNotNull();

    assertThat(processSpec.getTestResources()).hasSize(1);
    final byte[] bpmnResource = processSpec.getTestResources().get(0).getResource();

    final BpmnModelInstance deserializedProcess =
        Bpmn.readModelFromStream(new ByteArrayInputStream(bpmnResource));
    assertThat(deserializedProcess).isNotNull();
  }

  @Test
  void shouldTransformProcessSpec() throws IOException {
    // given
    final InputStream serializedProcessSpec =
        getClass().getResourceAsStream("/specs/test-spec-1.spec");
    assertThat(serializedProcessSpec).describedAs("Test resource not found").isNotNull();

    // when
    final ProcessSpec processSpec = parse(serializedProcessSpec);

    // then
    assertThat(processSpec).isNotNull();

    assertThat(processSpec.getTestResources()).hasSize(1);
    assertThat(processSpec.getTestCases()).hasSize(2);
  }
}
