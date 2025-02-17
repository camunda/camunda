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
package io.camunda.zeebe.client.impl.response;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.camunda.client.protocol.rest.EvaluatedDecisionInputItem;
import io.camunda.zeebe.client.api.JsonMapper;
import io.camunda.zeebe.client.api.response.EvaluatedDecisionInput;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass;

public class EvaluatedDecisionInputImpl implements EvaluatedDecisionInput {

  @JsonIgnore private JsonMapper jsonMapper;
  private final String inputId;
  private final String inputName;
  private final String inputValue;

  public EvaluatedDecisionInputImpl(
      final EvaluatedDecisionInputItem item, final JsonMapper jsonMapper) {
    this.jsonMapper = jsonMapper;
    inputId = item.getInputId();
    inputName = item.getInputName();
    inputValue = item.getInputValue();
  }

  public EvaluatedDecisionInputImpl(
      final JsonMapper jsonMapper, final GatewayOuterClass.EvaluatedDecisionInput evaluatedInput) {
    this.jsonMapper = jsonMapper;

    inputId = evaluatedInput.getInputId();
    inputName = evaluatedInput.getInputName();
    inputValue = evaluatedInput.getInputValue();
  }

  @Override
  public String getInputId() {
    return inputId;
  }

  @Override
  public String getInputName() {
    return inputName;
  }

  @Override
  public String getInputValue() {
    return inputValue;
  }

  @Override
  public String toJson() {
    return jsonMapper.toJson(this);
  }

  @Override
  public String toString() {
    return toJson();
  }
}
