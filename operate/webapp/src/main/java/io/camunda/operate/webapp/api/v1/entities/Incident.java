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
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import io.camunda.operate.entities.ErrorType;
import io.camunda.operate.entities.IncidentState;
import io.camunda.operate.schema.templates.IncidentTemplate;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Map;
import java.util.Objects;

@JsonInclude(Include.NON_NULL)
public class Incident {

  public static final String KEY = IncidentTemplate.KEY,
      PROCESS_DEFINITION_KEY = IncidentTemplate.PROCESS_DEFINITION_KEY,
      PROCESS_INSTANCE_KEY = IncidentTemplate.PROCESS_INSTANCE_KEY,
      TYPE = IncidentTemplate.ERROR_TYPE,
      MESSAGE = IncidentTemplate.ERROR_MSG,
      CREATION_TIME = IncidentTemplate.CREATION_TIME,
      STATE = IncidentTemplate.STATE,
      JOB_KEY = IncidentTemplate.JOB_KEY,
      TENANT_ID = IncidentTemplate.TENANT_ID;

  public static final String MESSAGE_FIELD = "message";
  public static final String TYPE_FIELD = "type";
  public static final Map<String, String> OBJECT_TO_SEARCH_MAP =
      Map.of(TYPE_FIELD, TYPE, MESSAGE_FIELD, MESSAGE);

  private Long key;
  private Long processDefinitionKey;
  private Long processInstanceKey;

  @Schema(implementation = ErrorType.class)
  private String type;

  private String message;
  private String creationTime;

  @Schema(implementation = IncidentState.class)
  private String state;

  private Long jobKey;
  private String tenantId;

  public Long getKey() {
    return key;
  }

  public Incident setKey(final Long key) {
    this.key = key;
    return this;
  }

  public Long getProcessDefinitionKey() {
    return processDefinitionKey;
  }

  public Incident setProcessDefinitionKey(final Long processDefinitionKey) {
    this.processDefinitionKey = processDefinitionKey;
    return this;
  }

  public Long getProcessInstanceKey() {
    return processInstanceKey;
  }

  public Incident setProcessInstanceKey(final Long processInstanceKey) {
    this.processInstanceKey = processInstanceKey;
    return this;
  }

  public String getType() {
    return type;
  }

  public Incident setType(final String type) {
    this.type = type;
    return this;
  }

  public String getMessage() {
    return message;
  }

  public Incident setMessage(final String message) {
    this.message = message;
    return this;
  }

  public String getCreationTime() {
    return creationTime;
  }

  public Incident setCreationTime(final String creationTime) {
    this.creationTime = creationTime;
    return this;
  }

  public String getState() {
    return state;
  }

  public Incident setState(final String state) {
    this.state = state;
    return this;
  }

  public Long getJobKey() {
    return jobKey;
  }

  public Incident setJobKey(final Long jobKey) {
    this.jobKey = jobKey;
    return this;
  }

  public String getTenantId() {
    return tenantId;
  }

  public Incident setTenantId(String tenantId) {
    this.tenantId = tenantId;
    return this;
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        key,
        processDefinitionKey,
        processInstanceKey,
        type,
        message,
        creationTime,
        state,
        jobKey,
        tenantId);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    Incident incident = (Incident) o;
    return Objects.equals(key, incident.key)
        && Objects.equals(processDefinitionKey, incident.processDefinitionKey)
        && Objects.equals(processInstanceKey, incident.processInstanceKey)
        && Objects.equals(type, incident.type)
        && Objects.equals(message, incident.message)
        && Objects.equals(creationTime, incident.creationTime)
        && Objects.equals(state, incident.state)
        && Objects.equals(jobKey, incident.jobKey)
        && Objects.equals(tenantId, incident.tenantId);
  }

  @Override
  public String toString() {
    return "Incident{"
        + "key="
        + key
        + ", processDefinitionKey="
        + processDefinitionKey
        + ", processInstanceKey="
        + processInstanceKey
        + ", type='"
        + type
        + '\''
        + ", message='"
        + message
        + '\''
        + ", creationTime='"
        + creationTime
        + '\''
        + ", state='"
        + state
        + '\''
        + ", jobKey='"
        + jobKey
        + '\''
        + ", tenantId='"
        + tenantId
        + '\''
        + '}';
  }
}
