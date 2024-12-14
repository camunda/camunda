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
import io.camunda.client.protocol.rest.MatchedDecisionRuleItem;
import io.camunda.zeebe.client.api.JsonMapper;
import io.camunda.zeebe.client.api.response.EvaluatedDecisionOutput;
import io.camunda.zeebe.client.api.response.MatchedDecisionRule;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class MatchedDecisionRuleImpl implements MatchedDecisionRule {

  @JsonIgnore private final JsonMapper jsonMapper;
  private final String ruleId;
  private final int ruleIndex;
  private final List<EvaluatedDecisionOutput> evaluatedOutputs = new ArrayList<>();

  public MatchedDecisionRuleImpl(
      final MatchedDecisionRuleItem ruleItem, final JsonMapper jsonMapper) {
    this.jsonMapper = jsonMapper;
    ruleId = ruleItem.getRuleId();
    ruleIndex = ruleItem.getRuleIndex();
    buildDecisionOutput(ruleItem);
  }

  public MatchedDecisionRuleImpl(
      final JsonMapper jsonMapper, final GatewayOuterClass.MatchedDecisionRule matchedRule) {
    this.jsonMapper = jsonMapper;

    ruleId = matchedRule.getRuleId();
    ruleIndex = matchedRule.getRuleIndex();

    matchedRule.getEvaluatedOutputsList().stream()
        .map(evaluatedOutput -> new EvaluatedDecisionOutputImpl(jsonMapper, evaluatedOutput))
        .forEach(evaluatedOutputs::add);
  }

  private void buildDecisionOutput(final MatchedDecisionRuleItem ruleItem) {
    if (ruleItem.getEvaluatedOutputs() == null) {
      return;
    }
    evaluatedOutputs.addAll(
        ruleItem.getEvaluatedOutputs().stream()
            .map(EvaluatedDecisionOutputImpl::new)
            .collect(Collectors.toList()));
  }

  @Override
  public String getRuleId() {
    return ruleId;
  }

  @Override
  public int getRuleIndex() {
    return ruleIndex;
  }

  @Override
  public List<EvaluatedDecisionOutput> getEvaluatedOutputs() {
    return evaluatedOutputs;
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
