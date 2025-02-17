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
import io.camunda.client.impl.util.ParseUtil;
import io.camunda.client.protocol.rest.EvaluatedDecisionResult;
import io.camunda.zeebe.client.api.JsonMapper;
import io.camunda.zeebe.client.api.response.EvaluatedDecision;
import io.camunda.zeebe.client.api.response.EvaluatedDecisionInput;
import io.camunda.zeebe.client.api.response.MatchedDecisionRule;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

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
      final EvaluatedDecisionResult evaluatedDecisionItem, final JsonMapper jsonMapper) {
    this.jsonMapper = jsonMapper;
    decisionId = evaluatedDecisionItem.getDecisionDefinitionId();
    decisionKey = ParseUtil.parseLongOrEmpty(evaluatedDecisionItem.getDecisionDefinitionKey());
    decisionVersion = evaluatedDecisionItem.getDecisionDefinitionVersion();
    decisionName = evaluatedDecisionItem.getDecisionDefinitionName();
    decisionType = evaluatedDecisionItem.getDecisionDefinitionType();
    decisionOutput = evaluatedDecisionItem.getOutput();
    tenantId = evaluatedDecisionItem.getTenantId();
    buildMatchedRules(evaluatedDecisionItem);
    buildEvaluatedDecisionInput(evaluatedDecisionItem);
  }

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

  private void buildEvaluatedDecisionInput(final EvaluatedDecisionResult evaluatedDecisionItem) {
    if (evaluatedDecisionItem.getEvaluatedInputs() == null) {
      return;
    }
    evaluatedInputs.addAll(
        evaluatedDecisionItem.getEvaluatedInputs().stream()
            .map(input -> new EvaluatedDecisionInputImpl(input, jsonMapper))
            .collect(Collectors.toList()));
  }

  private void buildMatchedRules(final EvaluatedDecisionResult evaluatedDecisionItem) {
    if (evaluatedDecisionItem.getMatchedRules() == null) {
      return;
    }
    matchedRules.addAll(
        evaluatedDecisionItem.getMatchedRules().stream()
            .map(rule -> new MatchedDecisionRuleImpl(rule, jsonMapper))
            .collect(Collectors.toList()));
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
  public String toJson() {
    return jsonMapper.toJson(this);
  }

  @Override
  public String getTenantId() {
    return tenantId;
  }

  @Override
  public String toString() {
    return toJson();
  }
}
