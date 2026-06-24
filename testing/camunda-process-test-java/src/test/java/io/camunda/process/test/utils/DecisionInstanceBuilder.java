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
package io.camunda.process.test.utils;

import io.camunda.client.api.response.EvaluatedDecisionInput;
import io.camunda.client.api.response.MatchedDecisionRule;
import io.camunda.client.api.search.response.DecisionDefinitionType;
import io.camunda.client.api.search.response.DecisionInstance;
import io.camunda.client.api.search.response.DecisionInstanceState;
import java.time.OffsetDateTime;
import java.util.List;

public class DecisionInstanceBuilder implements DecisionInstance {

  private static final OffsetDateTime EVALUATION_DATE =
      OffsetDateTime.parse("2025-03-20T13:26:00Z");

  private long decisionInstanceKey;
  private String decisionInstanceId;
  private DecisionInstanceState state;
  private OffsetDateTime evaluationDate;
  private String evaluationFailure;
  private Long processDefinitionKey;
  private Long processInstanceKey;
  private Long rootProcessInstanceKey;
  private Long elementInstanceKey;
  private long decisionDefinitionKey;
  private String decisionDefinitionId;
  private String decisionDefinitionName;
  private int decisionDefinitionVersion;
  private DecisionDefinitionType decisionDefinitionType;
  private long rootDecisionDefinitionKey;
  private String tenantId;
  private List<EvaluatedDecisionInput> evaluatedInputs;
  private List<MatchedDecisionRule> matchedRules;
  private String result;

  @Override
  public long getDecisionInstanceKey() {
    return decisionInstanceKey;
  }

  @Override
  public String getDecisionInstanceId() {
    return decisionInstanceId;
  }

  @Override
  public DecisionInstanceState getState() {
    return state;
  }

  @Override
  public OffsetDateTime getEvaluationDate() {
    return evaluationDate;
  }

  @Override
  public String getEvaluationFailure() {
    return evaluationFailure;
  }

  @Override
  public Long getProcessDefinitionKey() {
    return processDefinitionKey;
  }

  @Override
  public Long getProcessInstanceKey() {
    return processInstanceKey;
  }

  @Override
  public Long getRootProcessInstanceKey() {
    return rootProcessInstanceKey;
  }

  @Override
  public Long getElementInstanceKey() {
    return elementInstanceKey;
  }

  @Override
  public long getDecisionDefinitionKey() {
    return decisionDefinitionKey;
  }

  @Override
  public String getDecisionDefinitionId() {
    return decisionDefinitionId;
  }

  @Override
  public String getDecisionDefinitionName() {
    return decisionDefinitionName;
  }

  @Override
  public int getDecisionDefinitionVersion() {
    return decisionDefinitionVersion;
  }

  @Override
  public DecisionDefinitionType getDecisionDefinitionType() {
    return decisionDefinitionType;
  }

  @Override
  public long getRootDecisionDefinitionKey() {
    return rootDecisionDefinitionKey;
  }

  @Override
  public String getTenantId() {
    return tenantId;
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
  public String getResult() {
    return result;
  }

  @Override
  public String toJson() {
    return "{}";
  }

  public DecisionInstanceBuilder setResult(final String result) {
    this.result = result;
    return this;
  }

  public DecisionInstanceBuilder setMatchedRules(final List<MatchedDecisionRule> matchedRules) {
    this.matchedRules = matchedRules;
    return this;
  }

  public DecisionInstanceBuilder setEvaluatedInputs(
      final List<EvaluatedDecisionInput> evaluatedInputs) {
    this.evaluatedInputs = evaluatedInputs;
    return this;
  }

  public DecisionInstanceBuilder setTenantId(final String tenantId) {
    this.tenantId = tenantId;
    return this;
  }

  public DecisionInstanceBuilder setRootDecisionDefinitionKey(
      final long rootDecisionDefinitionKey) {
    this.rootDecisionDefinitionKey = rootDecisionDefinitionKey;
    return this;
  }

  public DecisionInstanceBuilder setDecisionDefinitionType(
      final DecisionDefinitionType decisionDefinitionType) {
    this.decisionDefinitionType = decisionDefinitionType;
    return this;
  }

  public DecisionInstanceBuilder setDecisionDefinitionVersion(final int decisionDefinitionVersion) {
    this.decisionDefinitionVersion = decisionDefinitionVersion;
    return this;
  }

  public DecisionInstanceBuilder setDecisionDefinitionName(final String decisionDefinitionName) {
    this.decisionDefinitionName = decisionDefinitionName;
    return this;
  }

  public DecisionInstanceBuilder setDecisionDefinitionId(final String decisionDefinitionId) {
    this.decisionDefinitionId = decisionDefinitionId;
    return this;
  }

  public DecisionInstanceBuilder setDecisionDefinitionKey(final long decisionDefinitionKey) {
    this.decisionDefinitionKey = decisionDefinitionKey;
    return this;
  }

  public DecisionInstanceBuilder setElementInstanceKey(final Long elementInstanceKey) {
    this.elementInstanceKey = elementInstanceKey;
    return this;
  }

  public DecisionInstanceBuilder setRootProcessInstanceKey(final Long rootProcessInstanceKey) {
    this.rootProcessInstanceKey = rootProcessInstanceKey;
    return this;
  }

  public DecisionInstanceBuilder setProcessInstanceKey(final Long processInstanceKey) {
    this.processInstanceKey = processInstanceKey;
    return this;
  }

  public DecisionInstanceBuilder setProcessDefinitionKey(final Long processDefinitionKey) {
    this.processDefinitionKey = processDefinitionKey;
    return this;
  }

  public DecisionInstanceBuilder setEvaluationFailure(final String evaluationFailure) {
    this.evaluationFailure = evaluationFailure;
    return this;
  }

  public DecisionInstanceBuilder setEvaluationDate(final OffsetDateTime evaluationDate) {
    this.evaluationDate = evaluationDate;
    return this;
  }

  public DecisionInstanceBuilder setState(final DecisionInstanceState state) {
    this.state = state;
    return this;
  }

  public DecisionInstanceBuilder setDecisionInstanceId(final String decisionInstanceId) {
    this.decisionInstanceId = decisionInstanceId;
    return this;
  }

  public DecisionInstanceBuilder setDecisionInstanceKey(final long decisionInstanceKey) {
    this.decisionInstanceKey = decisionInstanceKey;
    return this;
  }

  public DecisionInstance build() {
    return this;
  }

  public static DecisionInstanceBuilder newEvaluatedDecisionInstance(
      final long decisionInstanceKey) {
    return new DecisionInstanceBuilder()
        .setDecisionInstanceKey(decisionInstanceKey)
        .setState(DecisionInstanceState.EVALUATED)
        .setEvaluationDate(EVALUATION_DATE);
  }
}
