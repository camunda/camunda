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
package io.camunda.client.impl.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

import io.camunda.client.api.command.CompleteAdHocSubProcessResultStep1;
import io.camunda.client.api.command.CompleteAdHocSubProcessResultStep1.CompleteAdHocSubProcessResultStep2;
import io.camunda.client.impl.CamundaObjectMapper;
import io.camunda.client.util.JsonUtil;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import org.junit.jupiter.api.Test;

class CompleteAdHocSubProcessJobResultImplTest {

  @Test
  void shouldScopeAddedVariablesToLatestActivatedElement() {
    // given
    final CompleteAdHocSubProcessJobResultImpl result =
        new CompleteAdHocSubProcessJobResultImpl(new CamundaObjectMapper());
    final CompleteAdHocSubProcessResultStep1 publicResult = result;

    // when
    publicResult
        .activateElement("first")
        .addVariable("first", 1)
        .addVariable("second", 2)
        .activateElement("second")
        .addVariables(Collections.singletonMap("third", 3))
        .activateElement("third");

    // then
    assertThat(result.getActivateElements())
        .extracting(CompleteAdHocSubProcessJobResultImpl.ActivateElement::getElementId)
        .containsExactly("first", "second", "third");
    assertThat(JsonUtil.fromJsonAsMap(result.getActivateElements().get(0).getVariables()))
        .containsOnly(entry("first", 1), entry("second", 2));
    assertThat(JsonUtil.fromJsonAsMap(result.getActivateElements().get(1).getVariables()))
        .containsOnly(entry("third", 3));
    assertThat(result.getActivateElements().get(2).getVariables()).isNull();
  }

  @Test
  void shouldAccumulateVariablesAfterEverySeedMethod() {
    // given
    final CompleteAdHocSubProcessJobResultImpl result =
        new CompleteAdHocSubProcessJobResultImpl(new CamundaObjectMapper());
    final CompleteAdHocSubProcessResultStep1 publicResult = result;

    // when
    final CompleteAdHocSubProcessResultStep2 jsonElement = publicResult.activateElement("json");
    jsonElement.variables("{\"seed\":1}");
    jsonElement.addVariable("added", 2);

    final CompleteAdHocSubProcessResultStep2 streamElement = publicResult.activateElement("stream");
    streamElement.variables(
        new ByteArrayInputStream("{\"seed\":1}".getBytes(StandardCharsets.UTF_8)));
    streamElement.addVariable("added", 2);

    final CompleteAdHocSubProcessResultStep2 mapElement = publicResult.activateElement("map");
    mapElement.variables(Collections.singletonMap("seed", 1));
    mapElement.addVariable("added", 2);

    final CompleteAdHocSubProcessResultStep2 objectElement = publicResult.activateElement("object");
    objectElement.variables(new VariablesDocument());
    objectElement.addVariable("added", 2);

    final CompleteAdHocSubProcessResultStep2 variableElement =
        publicResult.activateElement("variable");
    variableElement.variable("seed", 1);
    variableElement.addVariable("added", 2);

    // then
    assertThat(result.getActivateElements())
        .allSatisfy(
            element ->
                assertThat(JsonUtil.fromJsonAsMap(element.getVariables()))
                    .containsOnly(entry("seed", 1), entry("added", 2)));
  }

  private static final class VariablesDocument {

    public int getSeed() {
      return 1;
    }
  }
}
