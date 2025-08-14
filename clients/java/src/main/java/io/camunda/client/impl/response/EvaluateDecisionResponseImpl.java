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
package io.camunda.client.impl.response;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.camunda.client.api.JsonMapper;
import io.camunda.client.api.response.EvaluateDecisionResponse;
import io.camunda.client.api.response.EvaluatedDecision;
import io.camunda.client.protocol.rest.EvaluateDecisionResult;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class EvaluateDecisionResponseImpl implements EvaluateDecisionResponse {

  @JsonIgnore private final JsonMapper jsonMapper;
  private final String decisionId;
  private final long decisionKey;
  private final int decisionVersion;
  private final String decisionName;
  private final String decisionRequirementsId;
  private final long decisionRequirementsKey;
  private final String decisionOutput;
  private final List<EvaluatedDecision> evaluatedDecisions = new ArrayList<>();
  private final String failedDecisionId;
  private final String failureMessage;
  private final String tenantId;
  private final long decisionInstanceKey;
  private final long decisionEvaluationKey;

  public EvaluateDecisionResponseImpl(
      final EvaluateDecisionResult response, final JsonMapper jsonMapper) {
    this.jsonMapper = jsonMapper;
    decisionId = response.getDecisionDefinitionId();
    decisionKey = Long.parseLong(response.getDecisionDefinitionKey());
    decisionVersion = response.getDecisionDefinitionVersion();
    decisionName = response.getDecisionDefinitionName();
    decisionRequirementsId = response.getDecisionRequirementsId();
    decisionRequirementsKey = Long.parseLong(response.getDecisionRequirementsKey());
    decisionOutput = response.getOutput();
    failedDecisionId = response.getFailedDecisionDefinitionId();
    failureMessage = response.getFailureMessage();
    tenantId = response.getTenantId();
    decisionInstanceKey = Long.parseLong(response.getDecisionInstanceKey());
    decisionEvaluationKey = Long.parseLong(response.getDecisionEvaluationKey());
    buildEvaluatedDecisions(response);
  }

  public EvaluateDecisionResponseImpl(
      final JsonMapper jsonMapper, final GatewayOuterClass.EvaluateDecisionResponse response) {
    this.jsonMapper = jsonMapper;

    decisionId = response.getDecisionId();
    decisionKey = response.getDecisionKey();
    decisionVersion = response.getDecisionVersion();
    decisionName = response.getDecisionName();
    decisionRequirementsId = response.getDecisionRequirementsId();
    decisionRequirementsKey = response.getDecisionRequirementsKey();
    decisionOutput = response.getDecisionOutput();
    failedDecisionId = response.getFailedDecisionId();
    failureMessage = response.getFailureMessage();
    tenantId = response.getTenantId();
    decisionInstanceKey = response.getDecisionInstanceKey();
    decisionEvaluationKey = response.getDecisionEvaluationKey();

    response.getEvaluatedDecisionsList().stream()
        .map(evaluatedDecision -> new EvaluatedDecisionImpl(jsonMapper, evaluatedDecision))
        .forEach(evaluatedDecisions::add);
  }

  private void buildEvaluatedDecisions(final EvaluateDecisionResult response) {
    if (response.getEvaluatedDecisions() == null) {
      return;
    }
    evaluatedDecisions.addAll(
        response.getEvaluatedDecisions().stream()
            .map(decision -> new EvaluatedDecisionImpl(decision, jsonMapper))
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
  public String getDecisionRequirementsId() {
    return decisionRequirementsId;
  }

  @Override
  public long getDecisionRequirementsKey() {
    return decisionRequirementsKey;
  }

  @Override
  public String getDecisionOutput() {
    return decisionOutput;
  }

  @Override
  public List<EvaluatedDecision> getEvaluatedDecisions() {
    return evaluatedDecisions;
  }

  @Override
  public String getFailedDecisionId() {
    return failedDecisionId;
  }

  @Override
  public String getFailureMessage() {
    return failureMessage;
  }

  @Override
  public String getTenantId() {
    return tenantId;
  }

  @Override
  public long getDecisionInstanceKey() {
    return decisionInstanceKey;
  }

  @Override
  public long getDecisionEvaluationKey() {
    return decisionEvaluationKey;
  }
}
