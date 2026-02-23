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
package io.camunda.client.impl.search.response;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.camunda.client.api.JsonMapper;
import io.camunda.client.api.response.EvaluatedDecisionInput;
import io.camunda.client.api.response.MatchedDecisionRule;
import io.camunda.client.api.search.response.DecisionDefinitionType;
import io.camunda.client.api.search.response.DecisionInstance;
import io.camunda.client.api.search.response.DecisionInstanceState;
import io.camunda.client.impl.response.EvaluatedDecisionInputImpl;
import io.camunda.client.impl.response.MatchedDecisionRuleImpl;
import io.camunda.client.impl.util.EnumUtil;
import io.camunda.client.impl.util.ParseUtil;
import io.camunda.client.protocol.rest.DecisionDefinitionTypeEnum;
import io.camunda.client.protocol.rest.DecisionInstanceGetQueryResult;
import io.camunda.client.protocol.rest.DecisionInstanceResult;
import io.camunda.client.protocol.rest.DecisionInstanceStateEnum;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class DecisionInstanceImpl implements DecisionInstance {

  @JsonIgnore private final JsonMapper jsonMapper;
  private final long decisionInstanceKey;
  private final String decisionInstanceId;
  private final DecisionInstanceState state;
  private final OffsetDateTime evaluationDate;
  private final String evaluationFailure;
  private final Long processDefinitionKey;
  private final Long processInstanceKey;
  private final Long rootProcessInstanceKey;
  private final Long elementInstanceKey;
  private final long decisionKey;
  private final String decisionDefinitionId;
  private final String decisionDefinitionName;
  private final int decisionDefinitionVersion;
  private final DecisionDefinitionType decisionDefinitionType;
  private final long rootDecisionDefinitionKey;
  private final String tenantId;
  private final List<EvaluatedDecisionInput> evaluatedInputs;
  private final List<MatchedDecisionRule> matchedRules;
  private final String result;

  public DecisionInstanceImpl(final DecisionInstanceResult item, final JsonMapper jsonMapper) {
    this(
        jsonMapper,
        Long.parseLong(item.getDecisionEvaluationKey()),
        item.getDecisionEvaluationInstanceKey(),
        toDecisionInstanceState(item.getState()),
        ParseUtil.parseOffsetDateTimeOrNull(item.getEvaluationDate()),
        item.getEvaluationFailure(),
        Long.parseLong(item.getProcessDefinitionKey()),
        Long.parseLong(item.getProcessInstanceKey()),
        ParseUtil.parseLongOrNull(item.getRootProcessInstanceKey()),
        Long.parseLong(item.getElementInstanceKey()),
        Long.parseLong(item.getDecisionDefinitionKey()),
        item.getDecisionDefinitionId(),
        item.getDecisionDefinitionName(),
        item.getDecisionDefinitionVersion(),
        toDecisionDefinitionType(item.getDecisionDefinitionType()),
        Long.parseLong(item.getRootDecisionDefinitionKey()),
        item.getTenantId(),
        null,
        null,
        item.getResult());
  }

  public DecisionInstanceImpl(
      final DecisionInstanceGetQueryResult item, final JsonMapper jsonMapper) {
    this(
        jsonMapper,
        Long.parseLong(item.getDecisionEvaluationKey()),
        item.getDecisionEvaluationInstanceKey(),
        toDecisionInstanceState(item.getState()),
        ParseUtil.parseOffsetDateTimeOrNull(item.getEvaluationDate()),
        item.getEvaluationFailure(),
        Long.parseLong(item.getProcessDefinitionKey()),
        Long.parseLong(item.getProcessInstanceKey()),
        ParseUtil.parseLongOrNull(item.getRootProcessInstanceKey()),
        Long.parseLong(item.getElementInstanceKey()),
        Long.parseLong(item.getDecisionDefinitionKey()),
        item.getDecisionDefinitionId(),
        item.getDecisionDefinitionName(),
        item.getDecisionDefinitionVersion(),
        toDecisionDefinitionType(item.getDecisionDefinitionType()),
        Long.parseLong(item.getRootDecisionDefinitionKey()),
        item.getTenantId(),
        item.getEvaluatedInputs().stream()
            .map(input -> new EvaluatedDecisionInputImpl(input, jsonMapper))
            .collect(Collectors.toList()),
        item.getMatchedRules().stream()
            .map(rule -> new MatchedDecisionRuleImpl(rule, jsonMapper))
            .collect(Collectors.toList()),
        item.getResult());
  }

  public DecisionInstanceImpl(
      final JsonMapper jsonMapper,
      final long decisionInstanceKey,
      final String decisionInstanceId,
      final DecisionInstanceState state,
      final OffsetDateTime evaluationDate,
      final String evaluationFailure,
      final Long processDefinitionKey,
      final Long processInstanceKey,
      final Long rootProcessInstanceKey,
      final Long elementInstanceKey,
      final long decisionKey,
      final String decisionDefinitionId,
      final String decisionDefinitionName,
      final int decisionDefinitionVersion,
      final DecisionDefinitionType decisionDefinitionType,
      final long rootDecisionDefinitionKey,
      final String tenantId,
      final List<EvaluatedDecisionInput> evaluatedInputs,
      final List<MatchedDecisionRule> matchedRules,
      final String result) {
    this.jsonMapper = jsonMapper;
    this.decisionInstanceKey = decisionInstanceKey;
    this.decisionInstanceId = decisionInstanceId;
    this.state = state;
    this.evaluationDate = evaluationDate;
    this.evaluationFailure = evaluationFailure;
    this.processDefinitionKey = processDefinitionKey;
    this.processInstanceKey = processInstanceKey;
    this.rootProcessInstanceKey = rootProcessInstanceKey;
    this.elementInstanceKey = elementInstanceKey;
    this.decisionKey = decisionKey;
    this.decisionDefinitionId = decisionDefinitionId;
    this.decisionDefinitionName = decisionDefinitionName;
    this.decisionDefinitionVersion = decisionDefinitionVersion;
    this.decisionDefinitionType = decisionDefinitionType;
    this.rootDecisionDefinitionKey = rootDecisionDefinitionKey;
    this.tenantId = tenantId;
    this.evaluatedInputs = evaluatedInputs;
    this.matchedRules = matchedRules;
    this.result = result;
  }

  private static DecisionDefinitionType toDecisionDefinitionType(
      final DecisionDefinitionTypeEnum decisionDefinitionType) {
    if (decisionDefinitionType == null) {
      return null;
    }
    switch (decisionDefinitionType) {
      case DECISION_TABLE:
        return DecisionDefinitionType.DECISION_TABLE;
      case LITERAL_EXPRESSION:
        return DecisionDefinitionType.LITERAL_EXPRESSION;
      case UNSPECIFIED:
        return DecisionDefinitionType.UNSPECIFIED;
      case UNKNOWN:
        return DecisionDefinitionType.UNKNOWN;
      case UNKNOWN_DEFAULT_OPEN_API:
      default:
        EnumUtil.logUnknownEnumValue(
            decisionDefinitionType, "decision definition type", DecisionDefinitionType.values());
        return DecisionDefinitionType.UNKNOWN_ENUM_VALUE;
    }
  }

  private static DecisionInstanceState toDecisionInstanceState(
      final DecisionInstanceStateEnum decisionInstanceState) {
    if (decisionInstanceState == null) {
      return null;
    }
    switch (decisionInstanceState) {
      case EVALUATED:
        return DecisionInstanceState.EVALUATED;
      case FAILED:
        return DecisionInstanceState.FAILED;
      case UNSPECIFIED:
        return DecisionInstanceState.UNSPECIFIED;
      case UNKNOWN:
        return DecisionInstanceState.UNKNOWN;
      case UNKNOWN_DEFAULT_OPEN_API:
      default:
        EnumUtil.logUnknownEnumValue(
            decisionInstanceState, "decision instance state", DecisionInstanceState.values());
        return DecisionInstanceState.UNKNOWN_ENUM_VALUE;
    }
  }

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
    return decisionKey;
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
    return jsonMapper.toJson(this);
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        decisionInstanceKey,
        decisionInstanceId,
        state,
        evaluationDate,
        evaluationFailure,
        processDefinitionKey,
        processInstanceKey,
        rootProcessInstanceKey,
        elementInstanceKey,
        decisionKey,
        decisionDefinitionId,
        decisionDefinitionName,
        decisionDefinitionVersion,
        decisionDefinitionType,
        tenantId,
        evaluatedInputs,
        matchedRules);
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final DecisionInstanceImpl that = (DecisionInstanceImpl) o;
    return decisionInstanceKey == that.decisionInstanceKey
        && decisionKey == that.decisionKey
        && decisionDefinitionVersion == that.decisionDefinitionVersion
        && state == that.state
        && Objects.equals(decisionInstanceId, that.decisionInstanceId)
        && Objects.equals(evaluationDate, that.evaluationDate)
        && Objects.equals(evaluationFailure, that.evaluationFailure)
        && Objects.equals(processDefinitionKey, that.processDefinitionKey)
        && Objects.equals(processInstanceKey, that.processInstanceKey)
        && Objects.equals(rootProcessInstanceKey, that.rootProcessInstanceKey)
        && Objects.equals(elementInstanceKey, that.elementInstanceKey)
        && Objects.equals(decisionDefinitionId, that.decisionDefinitionId)
        && Objects.equals(decisionDefinitionName, that.decisionDefinitionName)
        && decisionDefinitionType == that.decisionDefinitionType
        && Objects.equals(tenantId, that.tenantId)
        && Objects.equals(evaluatedInputs, that.evaluatedInputs)
        && Objects.equals(matchedRules, that.matchedRules);
  }
}
