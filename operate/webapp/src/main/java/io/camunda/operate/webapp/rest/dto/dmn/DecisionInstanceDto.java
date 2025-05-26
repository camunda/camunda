/*
 * Copyright Camunda Services GmbH
 *
 * BY INSTALLING, DOWNLOADING, ACCESSING, USING, OR DISTRIBUTING THE SOFTWARE (“USE”), YOU INDICATE YOUR ACCEPTANCE TO AND ARE ENTERING INTO A CONTRACT WITH, THE LICENSOR ON THE TERMS SET OUT IN THIS AGREEMENT. IF YOU DO NOT AGREE TO THESE TERMS, YOU MUST NOT USE THE SOFTWARE. IF YOU ARE RECEIVING THE SOFTWARE ON BEHALF OF A LEGAL ENTITY, YOU REPRESENT AND WARRANT THAT YOU HAVE THE ACTUAL AUTHORITY TO AGREE TO THE TERMS AND CONDITIONS OF THIS AGREEMENT ON BEHALF OF SUCH ENTITY.
 * “Licensee” means you, an individual, or the entity on whose behalf you receive the Software.
 *
 * Permission is hereby granted, free of charge, to the Licensee obtaining a copy of this Software and associated documentation files to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject in each case to the following conditions:
 * Condition 1: If the Licensee distributes the Software or any derivative works of the Software, the Licensee must attach this Agreement.
 * Condition 2: Without limiting other conditions in this Agreement, the grant of rights is solely for non-production use as defined below.
 * "Non-production use" means any use of the Software that is not directly related to creating products, services, or systems that generate revenue or other direct or indirect economic benefits.  Examples of permitted non-production use include personal use, educational use, research, and development. Examples of prohibited production use include, without limitation, use for commercial, for-profit, or publicly accessible systems or use for commercial or revenue-generating purposes.
 *
 * If the Licensee is in breach of the Conditions, this Agreement, including the rights granted under it, will automatically terminate with immediate effect.
 *
 * SUBJECT AS SET OUT BELOW, THE SOFTWARE IS PROVIDED “AS IS”, WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE, AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 * NOTHING IN THIS AGREEMENT EXCLUDES OR RESTRICTS A PARTY’S LIABILITY FOR (A) DEATH OR PERSONAL INJURY CAUSED BY THAT PARTY’S NEGLIGENCE, (B) FRAUD, OR (C) ANY OTHER LIABILITY TO THE EXTENT THAT IT CANNOT BE LAWFULLY EXCLUDED OR RESTRICTED.
 */
package io.camunda.operate.webapp.rest.dto.dmn;

import io.camunda.operate.entities.dmn.DecisionInstanceEntity;
import io.camunda.operate.entities.dmn.DecisionType;
import io.camunda.operate.webapp.rest.dto.CreatableFromEntity;
import io.camunda.operate.webapp.rest.dto.DtoCreator;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public class DecisionInstanceDto
    implements CreatableFromEntity<DecisionInstanceDto, DecisionInstanceEntity> {

  public static final Comparator<DecisionInstanceOutputDto>
      DECISION_INSTANCE_OUTPUT_DTO_COMPARATOR =
          Comparator.comparingInt(DecisionInstanceOutputDto::getRuleIndex)
              .thenComparing(DecisionInstanceOutputDto::getName);
  public static final Comparator<DecisionInstanceInputDto> DECISION_INSTANCE_INPUT_DTO_COMPARATOR =
      Comparator.comparing(DecisionInstanceInputDto::getName);

  private String id;
  private DecisionInstanceStateDto state;
  private DecisionType decisionType;
  private String decisionDefinitionId;
  private String decisionId;
  private String tenantId;
  private String decisionName;
  private int decisionVersion;
  private OffsetDateTime evaluationDate;
  private String errorMessage;
  private String processInstanceId;
  private String result;
  private List<DecisionInstanceInputDto> evaluatedInputs;
  private List<DecisionInstanceOutputDto> evaluatedOutputs;

  public String getId() {
    return id;
  }

  public DecisionInstanceDto setId(final String id) {
    this.id = id;
    return this;
  }

  public DecisionInstanceStateDto getState() {
    return state;
  }

  public DecisionInstanceDto setState(final DecisionInstanceStateDto state) {
    this.state = state;
    return this;
  }

  public DecisionType getDecisionType() {
    return decisionType;
  }

  public DecisionInstanceDto setDecisionType(final DecisionType decisionType) {
    this.decisionType = decisionType;
    return this;
  }

  public String getDecisionDefinitionId() {
    return decisionDefinitionId;
  }

  public DecisionInstanceDto setDecisionDefinitionId(final String decisionDefinitionId) {
    this.decisionDefinitionId = decisionDefinitionId;
    return this;
  }

  public String getDecisionId() {
    return decisionId;
  }

  public DecisionInstanceDto setDecisionId(final String decisionId) {
    this.decisionId = decisionId;
    return this;
  }

  public String getTenantId() {
    return tenantId;
  }

  public DecisionInstanceDto setTenantId(final String tenantId) {
    this.tenantId = tenantId;
    return this;
  }

  public String getDecisionName() {
    return decisionName;
  }

  public DecisionInstanceDto setDecisionName(final String decisionName) {
    this.decisionName = decisionName;
    return this;
  }

  public int getDecisionVersion() {
    return decisionVersion;
  }

  public DecisionInstanceDto setDecisionVersion(final int decisionVersion) {
    this.decisionVersion = decisionVersion;
    return this;
  }

  public OffsetDateTime getEvaluationDate() {
    return evaluationDate;
  }

  public DecisionInstanceDto setEvaluationDate(final OffsetDateTime evaluationDate) {
    this.evaluationDate = evaluationDate;
    return this;
  }

  public String getErrorMessage() {
    return errorMessage;
  }

  public DecisionInstanceDto setErrorMessage(final String errorMessage) {
    this.errorMessage = errorMessage;
    return this;
  }

  public String getProcessInstanceId() {
    return processInstanceId;
  }

  public DecisionInstanceDto setProcessInstanceId(final String processInstanceId) {
    this.processInstanceId = processInstanceId;
    return this;
  }

  public String getResult() {
    return result;
  }

  public DecisionInstanceDto setResult(final String result) {
    this.result = result;
    return this;
  }

  public List<DecisionInstanceInputDto> getEvaluatedInputs() {
    return evaluatedInputs;
  }

  public DecisionInstanceDto setEvaluatedInputs(
      final List<DecisionInstanceInputDto> evaluatedInputs) {
    this.evaluatedInputs = evaluatedInputs;
    return this;
  }

  public List<DecisionInstanceOutputDto> getEvaluatedOutputs() {
    return evaluatedOutputs;
  }

  public DecisionInstanceDto setEvaluatedOutputs(
      final List<DecisionInstanceOutputDto> evaluatedOutputs) {
    this.evaluatedOutputs = evaluatedOutputs;
    return this;
  }

  @Override
  public DecisionInstanceDto fillFrom(final DecisionInstanceEntity entity) {
    final List<DecisionInstanceInputDto> inputs =
        DtoCreator.create(entity.getEvaluatedInputs(), DecisionInstanceInputDto.class);
    Collections.sort(inputs, DECISION_INSTANCE_INPUT_DTO_COMPARATOR);

    final List<DecisionInstanceOutputDto> outputs =
        DtoCreator.create(entity.getEvaluatedOutputs(), DecisionInstanceOutputDto.class);
    Collections.sort(outputs, DECISION_INSTANCE_OUTPUT_DTO_COMPARATOR);

    final String evaluationFailureMessage =
        entity.getEvaluationFailureMessage() != null
            ? entity.getEvaluationFailureMessage()
            : entity.getEvaluationFailure();

    setId(entity.getId())
        .setDecisionDefinitionId(entity.getDecisionDefinitionId())
        .setDecisionId(entity.getDecisionId())
        .setTenantId(entity.getTenantId())
        .setDecisionName(entity.getDecisionName())
        .setDecisionType(entity.getDecisionType())
        .setDecisionVersion(entity.getDecisionVersion())
        .setErrorMessage(evaluationFailureMessage)
        .setEvaluationDate(entity.getEvaluationDate())
        .setEvaluatedInputs(inputs)
        .setEvaluatedOutputs(outputs)
        .setProcessInstanceId(String.valueOf(entity.getProcessInstanceKey()))
        .setResult(entity.getResult())
        .setState(DecisionInstanceStateDto.getState(entity.getState()));
    return this;
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        id,
        state,
        decisionType,
        decisionDefinitionId,
        decisionId,
        tenantId,
        decisionName,
        decisionVersion,
        evaluationDate,
        errorMessage,
        processInstanceId,
        result,
        evaluatedInputs,
        evaluatedOutputs);
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final DecisionInstanceDto that = (DecisionInstanceDto) o;
    return decisionVersion == that.decisionVersion
        && Objects.equals(id, that.id)
        && state == that.state
        && decisionType == that.decisionType
        && Objects.equals(decisionDefinitionId, that.decisionDefinitionId)
        && Objects.equals(decisionId, that.decisionId)
        && Objects.equals(tenantId, that.tenantId)
        && Objects.equals(decisionName, that.decisionName)
        && Objects.equals(evaluationDate, that.evaluationDate)
        && Objects.equals(errorMessage, that.errorMessage)
        && Objects.equals(processInstanceId, that.processInstanceId)
        && Objects.equals(result, that.result)
        && Objects.equals(evaluatedInputs, that.evaluatedInputs)
        && Objects.equals(evaluatedOutputs, that.evaluatedOutputs);
  }
}
