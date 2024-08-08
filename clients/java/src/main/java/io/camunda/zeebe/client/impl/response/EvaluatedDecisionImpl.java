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
import io.camunda.zeebe.client.api.response.EvaluatedDecision;
import io.camunda.zeebe.client.api.response.EvaluatedDecisionInput;
import io.camunda.zeebe.client.api.response.MatchedDecisionRule;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass;
import java.util.ArrayList;
import java.util.List;

public class EvaluatedDecisionImpl implements EvaluatedDecision {

  @JsonIgnore private final JsonMapper jsonMapper;
  private final String decisionId;
  private final long decisionKey;
  private final int decisionVersion;
  private final String decisionName;
  private final String decisionType;
  private final String decisionOutput;
  private final List<MatchedDecisionRule> matchedRules = new ArrayList<>();
  private final List<EvaluatedDecisionInput> evaluatedInputs = new ArrayList<>();
  private final String tenantId;

  public EvaluatedDecisionImpl(
      final JsonMapper jsonMapper, final GatewayOuterClass.EvaluatedDecision evaluatedDecision) {
    this.jsonMapper = jsonMapper;

    decisionId = evaluatedDecision.getDecisionId();
    decisionKey = evaluatedDecision.getDecisionKey();
    decisionName = evaluatedDecision.getDecisionName();
    decisionVersion = evaluatedDecision.getDecisionVersion();
    decisionType = evaluatedDecision.getDecisionType();
    decisionOutput = evaluatedDecision.getDecisionOutput();
    tenantId = evaluatedDecision.getTenantId();

    evaluatedDecision.getEvaluatedInputsList().stream()
        .map(evaluatedInput -> new EvaluatedDecisionInputImpl(jsonMapper, evaluatedInput))
        .forEach(evaluatedInputs::add);

    evaluatedDecision.getMatchedRulesList().stream()
        .map(matchedRule -> new MatchedDecisionRuleImpl(jsonMapper, matchedRule))
        .forEach(matchedRules::add);
  }

  @Override
  public String getDecisionId() {
    return decisionId;
  }

  @Override
  public int getDecisionVersion() {
    return decisionVersion;
  }

  @Override
  public long getDecisionKey() {
    return decisionKey;
  }

  @Override
  public String getDecisionName() {
    return decisionName;
  }

  @Override
  public String getDecisionType() {
    return decisionType;
  }

  @Override
  public String getDecisionOutput() {
    return decisionOutput;
  }

  @Override
  public List<EvaluatedDecisionInput> getEvaluatedInputs() {
    return evaluatedInputs;
  }

  @Override
  public List<MatchedDecisionRule> getMatchedRules() {
    return matchedRules;
  }

  @Override
  public String getTenantId() {
    return tenantId;
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
