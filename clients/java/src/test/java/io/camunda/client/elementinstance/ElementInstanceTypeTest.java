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
package io.camunda.client.elementinstance;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.protocol.rest.ElementInstanceFilter;
import io.camunda.client.protocol.rest.ElementInstanceResult;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

public class ElementInstanceTypeTest {

  @ParameterizedTest
  @EnumSource(BpmnElementType.class)
  void shouldHaveElementInstanceFilterType(final BpmnElementType bpmnElementType) {
    // when
    final ElementInstanceFilter.TypeEnum filterType =
        ElementInstanceFilter.TypeEnum.fromValue(bpmnElementType.name());

    // then
    assertThat(filterType)
        .describedAs(
            "The enum ElementInstanceFilter should contain a value for: %s. "
                + "Probably, the BPMN element is new and need to be added to the REST specification (rest-api.yaml).",
            bpmnElementType)
        .isNotNull()
        .isNotEqualTo(ElementInstanceFilter.TypeEnum.UNKNOWN_DEFAULT_OPEN_API);
  }

  @ParameterizedTest
  @EnumSource(BpmnElementType.class)
  void shouldHaveElementInstanceResultType(final BpmnElementType bpmnElementType) {
    // when
    final ElementInstanceResult.TypeEnum resultType =
        ElementInstanceResult.TypeEnum.fromValue(bpmnElementType.name());

    // then
    assertThat(resultType)
        .describedAs(
            "The enum ElementInstanceResult should contain a value for: %s. "
                + "Probably, the BPMN element is new and need to be added to the REST specification (rest-api.yaml).",
            bpmnElementType)
        .isNotNull()
        .isNotEqualTo(ElementInstanceResult.TypeEnum.UNKNOWN_DEFAULT_OPEN_API);
  }
}
