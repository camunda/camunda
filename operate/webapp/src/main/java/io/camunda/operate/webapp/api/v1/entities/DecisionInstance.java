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
package io.camunda.operate.webapp.api.v1.entities;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.camunda.operate.entities.dmn.DecisionType;
import io.camunda.operate.schema.templates.DecisionInstanceTemplate;
import java.util.List;
import java.util.Objects;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class DecisionInstance {

  // Used for index field search and sorting
  public static final String ID = DecisionInstanceTemplate.ID,
      KEY = DecisionInstanceTemplate.KEY,
      STATE = DecisionInstanceTemplate.STATE,
      EVALUATION_DATE = DecisionInstanceTemplate.EVALUATION_DATE,
      EVALUATION_FAILURE = DecisionInstanceTemplate.EVALUATION_FAILURE,
      PROCESS_DEFINITION_KEY = DecisionInstanceTemplate.PROCESS_DEFINITION_KEY,
      PROCESS_INSTANCE_KEY = DecisionInstanceTemplate.PROCESS_INSTANCE_KEY,
      DECISION_ID = DecisionInstanceTemplate.DECISION_ID,
      TENANT_ID = DecisionInstanceTemplate.TENANT_ID,
      DECISION_DEFINITION_ID = DecisionInstanceTemplate.DECISION_DEFINITION_ID,
      DECISION_NAME = DecisionInstanceTemplate.DECISION_NAME,
      DECISION_VERSION = DecisionInstanceTemplate.DECISION_VERSION,
      DECISION_TYPE = DecisionInstanceTemplate.DECISION_TYPE;

  private String id;
  private Long key;
  private DecisionInstanceState state;
  private String evaluationDate;
  private String evaluationFailure;
  private Long processDefinitionKey;
  private Long processInstanceKey;
  private String decisionId;
  private String decisionDefinitionId;
  private String decisionName;
  private Integer decisionVersion;
  private DecisionType decisionType;
  private String result;
  private List<DecisionInstanceInput> evaluatedInputs;
  private List<DecisionInstanceOutput> evaluatedOutputs;
  private String tenantId;

  public String getId() {
    return id;
  }

  public DecisionInstance setId(String id) {
    this.id = id;
    return this;
  }

  public Long getKey() {
    return key;
  }

  public DecisionInstance setKey(long key) {
    this.key = key;
    return this;
  }

  public DecisionInstanceState getState() {
    return state;
  }

  public DecisionInstance setState(DecisionInstanceState state) {
    this.state = state;
    return this;
  }

  public String getEvaluationDate() {
    return evaluationDate;
  }

  public DecisionInstance setEvaluationDate(String evaluationDate) {
    this.evaluationDate = evaluationDate;
    return this;
  }

  public String getEvaluationFailure() {
    return evaluationFailure;
  }

  public DecisionInstance setEvaluationFailure(String evaluationFailure) {
    this.evaluationFailure = evaluationFailure;
    return this;
  }

  public Long getProcessDefinitionKey() {
    return processDefinitionKey;
  }

  public DecisionInstance setProcessDefinitionKey(long processDefinitionKey) {
    this.processDefinitionKey = processDefinitionKey;
    return this;
  }

  public Long getProcessInstanceKey() {
    return processInstanceKey;
  }

  public DecisionInstance setProcessInstanceKey(long processInstanceKey) {
    this.processInstanceKey = processInstanceKey;
    return this;
  }

  public String getDecisionId() {
    return decisionId;
  }

  public DecisionInstance setDecisionId(String decisionId) {
    this.decisionId = decisionId;
    return this;
  }

  public String getDecisionDefinitionId() {
    return decisionDefinitionId;
  }

  public DecisionInstance setDecisionDefinitionId(String decisionDefinitionId) {
    this.decisionDefinitionId = decisionDefinitionId;
    return this;
  }

  public String getDecisionName() {
    return decisionName;
  }

  public DecisionInstance setDecisionName(String decisionName) {
    this.decisionName = decisionName;
    return this;
  }

  public Integer getDecisionVersion() {
    return decisionVersion;
  }

  public DecisionInstance setDecisionVersion(int decisionVersion) {
    this.decisionVersion = decisionVersion;
    return this;
  }

  public DecisionType getDecisionType() {
    return decisionType;
  }

  public DecisionInstance setDecisionType(DecisionType decisionType) {
    this.decisionType = decisionType;
    return this;
  }

  public String getResult() {
    return result;
  }

  public DecisionInstance setResult(String result) {
    this.result = result;
    return this;
  }

  public List<DecisionInstanceInput> getEvaluatedInputs() {
    return evaluatedInputs;
  }

  public DecisionInstance setEvaluatedInputs(List<DecisionInstanceInput> evaluatedInputs) {
    this.evaluatedInputs = evaluatedInputs;
    return this;
  }

  public List<DecisionInstanceOutput> getEvaluatedOutputs() {
    return evaluatedOutputs;
  }

  public DecisionInstance setEvaluatedOutputs(List<DecisionInstanceOutput> evaluatedOutputs) {
    this.evaluatedOutputs = evaluatedOutputs;
    return this;
  }

  public String getTenantId() {
    return tenantId;
  }

  public DecisionInstance setTenantId(String tenantId) {
    this.tenantId = tenantId;
    return this;
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        id,
        key,
        state,
        evaluationDate,
        evaluationFailure,
        processDefinitionKey,
        processInstanceKey,
        decisionId,
        decisionDefinitionId,
        decisionName,
        decisionVersion,
        decisionType,
        result,
        evaluatedInputs,
        evaluatedOutputs,
        tenantId);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final DecisionInstance that = (DecisionInstance) o;
    return Objects.equals(id, that.id)
        && Objects.equals(key, that.key)
        && state == that.state
        && Objects.equals(evaluationDate, that.evaluationDate)
        && Objects.equals(evaluationFailure, that.evaluationFailure)
        && Objects.equals(processDefinitionKey, that.processDefinitionKey)
        && Objects.equals(processInstanceKey, that.processInstanceKey)
        && Objects.equals(decisionId, that.decisionId)
        && Objects.equals(decisionDefinitionId, that.decisionDefinitionId)
        && Objects.equals(decisionName, that.decisionName)
        && Objects.equals(decisionVersion, that.decisionVersion)
        && decisionType == that.decisionType
        && Objects.equals(result, that.result)
        && Objects.equals(evaluatedInputs, that.evaluatedInputs)
        && Objects.equals(evaluatedOutputs, that.evaluatedOutputs)
        && Objects.equals(tenantId, that.tenantId);
  }

  @Override
  public String toString() {
    return "DecisionInstance{"
        + "id='"
        + id
        + '\''
        + ", key="
        + key
        + ", state="
        + state
        + ", evaluationDate='"
        + evaluationDate
        + '\''
        + ", evaluationFailure='"
        + evaluationFailure
        + '\''
        + ", processDefinitionKey="
        + processDefinitionKey
        + ", processInstanceKey="
        + processInstanceKey
        + ", decisionId='"
        + decisionId
        + '\''
        + ", decisionDefinitionId='"
        + decisionDefinitionId
        + '\''
        + ", decisionName='"
        + decisionName
        + '\''
        + ", decisionVersion="
        + decisionVersion
        + ", decisionType="
        + decisionType
        + ", result='"
        + result
        + '\''
        + ", evaluatedInputs="
        + evaluatedInputs
        + ", evaluatedOutputs="
        + evaluatedOutputs
        + ", tenantId='"
        + tenantId
        + '\''
        + '}';
  }
}
