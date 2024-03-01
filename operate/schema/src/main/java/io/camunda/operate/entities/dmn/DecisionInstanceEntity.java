/*
 * Copyright Camunda Services GmbH
 *
 * BY INSTALLING, DOWNLOADING, ACCESSING, USING, OR DISTRIBUTING THE SOFTWARE, YOU INDICATE YOUR ACCEPTANCE TO AND ARE ENTERING INTO A CONTRACT WITH, THE LICENSOR ON THE TERMS SET OUT IN THIS AGREEMENT. IF YOU DO NOT AGREE TO THESE TERMS, YOU MUST NOT USE THE SOFTWARE. IF YOU ARE RECEIVING THE SOFTWARE ON BEHALF OF A LEGAL ENTITY, YOU REPRESENT AND WARRANT THAT YOU HAVE THE ACTUAL AUTHORITY TO AGREE TO THE TERMS AND CONDITIONS OF THIS AGREEMENT ON BEHALF OF SUCH ENTITY.
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
package io.camunda.operate.entities.dmn;

import static io.camunda.operate.schema.indices.IndexDescriptor.DEFAULT_TENANT_ID;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.camunda.operate.entities.OperateZeebeEntity;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public class DecisionInstanceEntity extends OperateZeebeEntity<DecisionInstanceEntity> {

  private Integer executionIndex;
  private DecisionInstanceState state;
  private OffsetDateTime evaluationDate;
  private String evaluationFailure;
  private Long position;
  private long decisionRequirementsKey;
  private String decisionRequirementsId;
  private long processDefinitionKey;
  private long processInstanceKey;

  /** Attention! This field will be filled in only for data imported after v. 8.2.0. */
  private String bpmnProcessId;

  private long elementInstanceKey;
  private String elementId;
  private String decisionId;
  private String decisionDefinitionId;
  private String decisionName;
  private int decisionVersion;
  private String rootDecisionName;
  private String rootDecisionId;
  private String rootDecisionDefinitionId;
  private DecisionType decisionType;
  private String result;
  private List<DecisionInstanceInputEntity> evaluatedInputs = new ArrayList<>();
  private List<DecisionInstanceOutputEntity> evaluatedOutputs = new ArrayList<>();
  private String tenantId = DEFAULT_TENANT_ID;
  ;
  @JsonIgnore private Object[] sortValues;

  public static Long extractKey(String id) {
    return Long.valueOf(id.split("-")[0]);
  }

  public DecisionInstanceEntity setId(Long key, int executionIndex) {
    return setId(String.format("%d-%d", key, executionIndex));
  }

  public Integer getExecutionIndex() {
    return executionIndex;
  }

  public DecisionInstanceEntity setExecutionIndex(final Integer executionIndex) {
    this.executionIndex = executionIndex;
    return this;
  }

  public DecisionInstanceState getState() {
    return state;
  }

  public DecisionInstanceEntity setState(final DecisionInstanceState state) {
    this.state = state;
    return this;
  }

  public OffsetDateTime getEvaluationDate() {
    return evaluationDate;
  }

  public DecisionInstanceEntity setEvaluationDate(final OffsetDateTime evaluationDate) {
    this.evaluationDate = evaluationDate;
    return this;
  }

  public String getEvaluationFailure() {
    return evaluationFailure;
  }

  public DecisionInstanceEntity setEvaluationFailure(final String evaluationFailure) {
    this.evaluationFailure = evaluationFailure;
    return this;
  }

  public Long getPosition() {
    return position;
  }

  public DecisionInstanceEntity setPosition(final Long position) {
    this.position = position;
    return this;
  }

  public String getDecisionDefinitionId() {
    return decisionDefinitionId;
  }

  public DecisionInstanceEntity setDecisionDefinitionId(final String decisionDefinitionId) {
    this.decisionDefinitionId = decisionDefinitionId;
    return this;
  }

  public long getDecisionRequirementsKey() {
    return decisionRequirementsKey;
  }

  public DecisionInstanceEntity setDecisionRequirementsKey(final long decisionRequirementsKey) {
    this.decisionRequirementsKey = decisionRequirementsKey;
    return this;
  }

  public String getDecisionRequirementsId() {
    return decisionRequirementsId;
  }

  public DecisionInstanceEntity setDecisionRequirementsId(final String decisionRequirementsId) {
    this.decisionRequirementsId = decisionRequirementsId;
    return this;
  }

  public long getProcessDefinitionKey() {
    return processDefinitionKey;
  }

  public DecisionInstanceEntity setProcessDefinitionKey(final long processDefinitionKey) {
    this.processDefinitionKey = processDefinitionKey;
    return this;
  }

  public long getProcessInstanceKey() {
    return processInstanceKey;
  }

  public DecisionInstanceEntity setProcessInstanceKey(final long processInstanceKey) {
    this.processInstanceKey = processInstanceKey;
    return this;
  }

  public String getBpmnProcessId() {
    return bpmnProcessId;
  }

  public DecisionInstanceEntity setBpmnProcessId(String bpmnProcessId) {
    this.bpmnProcessId = bpmnProcessId;
    return this;
  }

  public long getElementInstanceKey() {
    return elementInstanceKey;
  }

  public DecisionInstanceEntity setElementInstanceKey(final long elementInstanceKey) {
    this.elementInstanceKey = elementInstanceKey;
    return this;
  }

  public String getElementId() {
    return elementId;
  }

  public DecisionInstanceEntity setElementId(final String elementId) {
    this.elementId = elementId;
    return this;
  }

  public String getDecisionId() {
    return decisionId;
  }

  public DecisionInstanceEntity setDecisionId(final String decisionId) {
    this.decisionId = decisionId;
    return this;
  }

  public String getDecisionName() {
    return decisionName;
  }

  public DecisionInstanceEntity setDecisionName(final String decisionName) {
    this.decisionName = decisionName;
    return this;
  }

  public int getDecisionVersion() {
    return decisionVersion;
  }

  public DecisionInstanceEntity setDecisionVersion(final int decisionVersion) {
    this.decisionVersion = decisionVersion;
    return this;
  }

  public String getRootDecisionName() {
    return rootDecisionName;
  }

  public DecisionInstanceEntity setRootDecisionName(final String rootDecisionName) {
    this.rootDecisionName = rootDecisionName;
    return this;
  }

  public String getRootDecisionId() {
    return rootDecisionId;
  }

  public DecisionInstanceEntity setRootDecisionId(final String rootDecisionId) {
    this.rootDecisionId = rootDecisionId;
    return this;
  }

  public String getRootDecisionDefinitionId() {
    return rootDecisionDefinitionId;
  }

  public DecisionInstanceEntity setRootDecisionDefinitionId(final String rootDecisionDefinitionId) {
    this.rootDecisionDefinitionId = rootDecisionDefinitionId;
    return this;
  }

  public DecisionType getDecisionType() {
    return decisionType;
  }

  public DecisionInstanceEntity setDecisionType(final DecisionType decisionType) {
    this.decisionType = decisionType;
    return this;
  }

  public String getResult() {
    return result;
  }

  public DecisionInstanceEntity setResult(final String result) {
    this.result = result;
    return this;
  }

  public List<DecisionInstanceInputEntity> getEvaluatedInputs() {
    return evaluatedInputs;
  }

  public DecisionInstanceEntity setEvaluatedInputs(
      final List<DecisionInstanceInputEntity> evaluatedInputs) {
    this.evaluatedInputs = evaluatedInputs;
    return this;
  }

  public List<DecisionInstanceOutputEntity> getEvaluatedOutputs() {
    return evaluatedOutputs;
  }

  public DecisionInstanceEntity setEvaluatedOutputs(
      final List<DecisionInstanceOutputEntity> evaluatedOutputs) {
    this.evaluatedOutputs = evaluatedOutputs;
    return this;
  }

  public Object[] getSortValues() {
    return sortValues;
  }

  public DecisionInstanceEntity setSortValues(final Object[] sortValues) {
    this.sortValues = sortValues;
    return this;
  }

  public String getTenantId() {
    return tenantId;
  }

  public DecisionInstanceEntity setTenantId(String tenantId) {
    this.tenantId = tenantId;
    return this;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    if (!super.equals(o)) return false;
    DecisionInstanceEntity that = (DecisionInstanceEntity) o;
    return decisionRequirementsKey == that.decisionRequirementsKey
        && processDefinitionKey == that.processDefinitionKey
        && processInstanceKey == that.processInstanceKey
        && elementInstanceKey == that.elementInstanceKey
        && decisionVersion == that.decisionVersion
        && Objects.equals(executionIndex, that.executionIndex)
        && state == that.state
        && Objects.equals(evaluationDate, that.evaluationDate)
        && Objects.equals(evaluationFailure, that.evaluationFailure)
        && Objects.equals(position, that.position)
        && Objects.equals(decisionRequirementsId, that.decisionRequirementsId)
        && Objects.equals(bpmnProcessId, that.bpmnProcessId)
        && Objects.equals(elementId, that.elementId)
        && Objects.equals(decisionId, that.decisionId)
        && Objects.equals(decisionDefinitionId, that.decisionDefinitionId)
        && Objects.equals(decisionName, that.decisionName)
        && Objects.equals(rootDecisionName, that.rootDecisionName)
        && Objects.equals(rootDecisionId, that.rootDecisionId)
        && Objects.equals(rootDecisionDefinitionId, that.rootDecisionDefinitionId)
        && decisionType == that.decisionType
        && Objects.equals(result, that.result)
        && Objects.equals(evaluatedInputs, that.evaluatedInputs)
        && Objects.equals(evaluatedOutputs, that.evaluatedOutputs)
        && Objects.equals(tenantId, that.tenantId)
        && Arrays.equals(sortValues, that.sortValues);
  }

  @Override
  public int hashCode() {
    int result1 =
        Objects.hash(
            super.hashCode(),
            executionIndex,
            state,
            evaluationDate,
            evaluationFailure,
            position,
            decisionRequirementsKey,
            decisionRequirementsId,
            processDefinitionKey,
            processInstanceKey,
            bpmnProcessId,
            elementInstanceKey,
            elementId,
            decisionId,
            decisionDefinitionId,
            decisionName,
            decisionVersion,
            rootDecisionName,
            rootDecisionId,
            rootDecisionDefinitionId,
            decisionType,
            result,
            evaluatedInputs,
            evaluatedOutputs,
            tenantId);
    result1 = 31 * result1 + Arrays.hashCode(sortValues);
    return result1;
  }
}
