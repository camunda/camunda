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
package io.camunda.operate.webapp.rest.dto.operation;

import io.camunda.operate.entities.OperationType;

public class CreateOperationRequestDto {

  private OperationType operationType;

  /** Batch operation name. */
  private String name;

  /** RESOLVE_INCIDENT operation. */
  private String incidentId;

  /** UPDATE_VARIABLE operation. */
  private String variableScopeId;

  private String variableName;
  private String variableValue;

  public CreateOperationRequestDto() {}

  public CreateOperationRequestDto(OperationType operationType) {
    this.operationType = operationType;
  }

  public OperationType getOperationType() {
    return operationType;
  }

  public CreateOperationRequestDto setOperationType(OperationType operationType) {
    this.operationType = operationType;
    return this;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getIncidentId() {
    return incidentId;
  }

  public void setIncidentId(String incidentId) {
    this.incidentId = incidentId;
  }

  public String getVariableScopeId() {
    return variableScopeId;
  }

  public void setVariableScopeId(String variableScopeId) {
    this.variableScopeId = variableScopeId;
  }

  public String getVariableName() {
    return variableName;
  }

  public void setVariableName(String variableName) {
    this.variableName = variableName;
  }

  public String getVariableValue() {
    return variableValue;
  }

  public void setVariableValue(String variableValue) {
    this.variableValue = variableValue;
  }

  @Override
  public int hashCode() {
    int result = operationType != null ? operationType.hashCode() : 0;
    result = 31 * result + (name != null ? name.hashCode() : 0);
    result = 31 * result + (incidentId != null ? incidentId.hashCode() : 0);
    result = 31 * result + (variableScopeId != null ? variableScopeId.hashCode() : 0);
    result = 31 * result + (variableName != null ? variableName.hashCode() : 0);
    result = 31 * result + (variableValue != null ? variableValue.hashCode() : 0);
    return result;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    final CreateOperationRequestDto that = (CreateOperationRequestDto) o;

    if (operationType != that.operationType) {
      return false;
    }
    if (name != null ? !name.equals(that.name) : that.name != null) {
      return false;
    }
    if (incidentId != null ? !incidentId.equals(that.incidentId) : that.incidentId != null) {
      return false;
    }
    if (variableScopeId != null
        ? !variableScopeId.equals(that.variableScopeId)
        : that.variableScopeId != null) {
      return false;
    }
    if (variableName != null
        ? !variableName.equals(that.variableName)
        : that.variableName != null) {
      return false;
    }
    return variableValue != null
        ? variableValue.equals(that.variableValue)
        : that.variableValue == null;
  }

  @Override
  public String toString() {
    return "CreateOperationRequestDto{"
        + "operationType="
        + operationType
        + ", name='"
        + name
        + '\''
        + ", incidentId='"
        + incidentId
        + '\''
        + ", variableScopeId='"
        + variableScopeId
        + '\''
        + ", variableName='"
        + variableName
        + '\''
        + ", variableValue='"
        + variableValue
        + '\''
        + '}';
  }
}
