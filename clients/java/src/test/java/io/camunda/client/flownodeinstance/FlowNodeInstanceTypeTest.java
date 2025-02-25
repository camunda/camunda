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
package io.camunda.client.flownodeinstance;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.protocol.rest.FlowNodeInstanceFilter;
import io.camunda.client.protocol.rest.FlowNodeInstanceResult;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

public class FlowNodeInstanceTypeTest {

  @ParameterizedTest
  @EnumSource(BpmnElementType.class)
  void shouldHaveFlowNodeInstanceFilterType(final BpmnElementType bpmnElementType) {
    // when
    final FlowNodeInstanceFilter.TypeEnum filterType =
        FlowNodeInstanceFilter.TypeEnum.fromValue(bpmnElementType.name());

    // then
    assertThat(filterType)
        .describedAs(
            "The enum FlowNodeInstanceFilter should contain a value for: %s. "
                + "Probably, the BPMN element is new and need to be added to the REST specification (rest-api.yaml).",
            bpmnElementType)
        .isNotNull()
        .isNotEqualTo(FlowNodeInstanceFilter.TypeEnum.UNKNOWN_DEFAULT_OPEN_API);
  }

  @ParameterizedTest
  @EnumSource(BpmnElementType.class)
  void shouldHaveFlowNodeInstanceResultType(final BpmnElementType bpmnElementType) {
    // when
    final FlowNodeInstanceResult.TypeEnum resultType =
        FlowNodeInstanceResult.TypeEnum.fromValue(bpmnElementType.name());

    // then
    assertThat(resultType)
        .describedAs(
            "The enum FlowNodeInstanceResult should contain a value for: %s. "
                + "Probably, the BPMN element is new and need to be added to the REST specification (rest-api.yaml).",
            bpmnElementType)
        .isNotNull()
        .isNotEqualTo(FlowNodeInstanceResult.TypeEnum.UNKNOWN_DEFAULT_OPEN_API);
  }
}
