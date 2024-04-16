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
package io.camunda.tasklist.zeebeimport.v860.record.value;

import io.camunda.tasklist.zeebeimport.v860.record.RecordValueWithPayloadImpl;
import io.camunda.zeebe.protocol.record.value.JobKind;
import io.camunda.zeebe.protocol.record.value.JobRecordValue;
import java.util.Map;
import java.util.Objects;

public class JobRecordValueImpl extends RecordValueWithPayloadImpl implements JobRecordValue {

  private String bpmnProcessId;
  private String elementId;
  private long elementInstanceKey;
  private long processInstanceKey;
  private long processDefinitionKey;
  private int processDefinitionVersion;
  private String type;
  private String worker;
  private long deadline;
  private Map<String, String> customHeaders;
  private int retries;
  private String errorMessage;
  private String errorCode;
  private String tenantId;
  private JobKind jobKind;

  public JobRecordValueImpl() {}

  @Override
  public long getProcessInstanceKey() {
    return processInstanceKey;
  }

  public void setProcessInstanceKey(final long processInstanceKey) {
    this.processInstanceKey = processInstanceKey;
  }

  @Override
  public String getType() {
    return type;
  }

  @Override
  public Map<String, String> getCustomHeaders() {
    return customHeaders;
  }

  @Override
  public String getWorker() {
    return worker;
  }

  @Override
  public int getRetries() {
    return retries;
  }

  @Override
  public long getRetryBackoff() {
    return 0;
  }

  @Override
  public long getRecurringTime() {
    return 0;
  }

  @Override
  public long getDeadline() {
    return deadline;
  }

  @Override
  public long getTimeout() {
    return 0;
  }

  @Override
  public String getErrorMessage() {
    return errorMessage;
  }

  @Override
  public String getErrorCode() {
    return errorCode;
  }

  @Override
  public String getElementId() {
    return elementId;
  }

  @Override
  public long getElementInstanceKey() {
    return elementInstanceKey;
  }

  @Override
  public String getBpmnProcessId() {
    return bpmnProcessId;
  }

  @Override
  public int getProcessDefinitionVersion() {
    return processDefinitionVersion;
  }

  @Override
  public long getProcessDefinitionKey() {
    return processDefinitionKey;
  }

  public void setProcessDefinitionKey(final long processDefinitionKey) {
    this.processDefinitionKey = processDefinitionKey;
  }

  @Override
  public JobKind getJobKind() {
    return jobKind;
  }

  public JobRecordValueImpl setJobKind(final JobKind jobKind) {
    this.jobKind = jobKind;
    return this;
  }

  public void setProcessDefinitionVersion(final int processDefinitionVersion) {
    this.processDefinitionVersion = processDefinitionVersion;
  }

  public void setBpmnProcessId(final String bpmnProcessId) {
    this.bpmnProcessId = bpmnProcessId;
  }

  public void setElementInstanceKey(final long elementInstanceKey) {
    this.elementInstanceKey = elementInstanceKey;
  }

  public void setElementId(final String elementId) {
    this.elementId = elementId;
  }

  public void setErrorCode(final String errorCode) {
    this.errorCode = errorCode;
  }

  public void setErrorMessage(final String errorMessage) {
    this.errorMessage = errorMessage;
  }

  public void setDeadline(final long deadline) {
    this.deadline = deadline;
  }

  public void setRetries(final int retries) {
    this.retries = retries;
  }

  public void setWorker(final String worker) {
    this.worker = worker;
  }

  public void setCustomHeaders(final Map<String, String> customHeaders) {
    this.customHeaders = customHeaders;
  }

  public void setType(final String type) {
    this.type = type;
  }

  @Override
  public String getTenantId() {
    return tenantId;
  }

  public void setTenantId(final String tenantId) {
    this.tenantId = tenantId;
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        super.hashCode(),
        bpmnProcessId,
        elementId,
        elementInstanceKey,
        processInstanceKey,
        processDefinitionKey,
        processDefinitionVersion,
        type,
        worker,
        deadline,
        customHeaders,
        retries,
        errorMessage,
        errorCode,
        tenantId,
        jobKind);
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    if (!super.equals(o)) {
      return false;
    }
    final JobRecordValueImpl that = (JobRecordValueImpl) o;
    return elementInstanceKey == that.elementInstanceKey
        && processInstanceKey == that.processInstanceKey
        && processDefinitionKey == that.processDefinitionKey
        && processDefinitionVersion == that.processDefinitionVersion
        && deadline == that.deadline
        && retries == that.retries
        && Objects.equals(bpmnProcessId, that.bpmnProcessId)
        && Objects.equals(elementId, that.elementId)
        && Objects.equals(type, that.type)
        && Objects.equals(worker, that.worker)
        && Objects.equals(customHeaders, that.customHeaders)
        && Objects.equals(errorMessage, that.errorMessage)
        && Objects.equals(errorCode, that.errorCode)
        && Objects.equals(tenantId, that.tenantId)
        && jobKind == that.jobKind;
  }

  @Override
  public String toString() {
    return "JobRecordValueImpl{"
        + "bpmnProcessId='"
        + bpmnProcessId
        + '\''
        + ", elementId='"
        + elementId
        + '\''
        + ", elementInstanceKey="
        + elementInstanceKey
        + ", processInstanceKey="
        + processInstanceKey
        + ", processDefinitionKey="
        + processDefinitionKey
        + ", processDefinitionVersion="
        + processDefinitionVersion
        + ", type='"
        + type
        + '\''
        + ", worker='"
        + worker
        + '\''
        + ", deadline="
        + deadline
        + ", customHeaders="
        + customHeaders
        + ", retries="
        + retries
        + ", errorMessage='"
        + errorMessage
        + '\''
        + ", errorCode='"
        + errorCode
        + '\''
        + ", tenantId='"
        + tenantId
        + '\''
        + ", jobKind="
        + jobKind
        + '}';
  }
}
