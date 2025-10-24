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
import io.camunda.zeebe.client.api.JsonMapper;
import io.camunda.zeebe.client.api.response.EvaluatedDecisionOutput;
import io.camunda.zeebe.client.protocol.rest.EvaluatedDecisionOutputItem;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass;

/**
 * @deprecated since 8.8 for removal in 8.10, replaced by {@link
 *     io.camunda.client.impl.response.EvaluatedDecisionOutputImpl}. Please see the <a
 *     href="https://docs.camunda.io/docs/8.8/apis-tools/migration-manuals/migrate-to-camunda-java-client/">Camunda
 *     Java Client migration guide</a>
 */
@Deprecated
public class EvaluatedDecisionOutputImpl implements EvaluatedDecisionOutput {

  @JsonIgnore private JsonMapper jsonMapper;
  private final String outputId;
  private final String outputName;
  private final String outputValue;

  public EvaluatedDecisionOutputImpl(final EvaluatedDecisionOutputItem item) {
    this.outputId = item.getOutputId();
    this.outputName = item.getOutputName();
    this.outputValue = item.getOutputValue();
  }

  public EvaluatedDecisionOutputImpl(
      final JsonMapper jsonMapper,
      final GatewayOuterClass.EvaluatedDecisionOutput evaluatedOutput) {
    this.jsonMapper = jsonMapper;

    outputId = evaluatedOutput.getOutputId();
    outputName = evaluatedOutput.getOutputName();
    outputValue = evaluatedOutput.getOutputValue();
  }

  @Override
  public String getOutputId() {
    return outputId;
  }

  @Override
  public String getOutputName() {
    return outputName;
  }

  @Override
  public String getOutputValue() {
    return outputValue;
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
