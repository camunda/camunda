/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record.value.job;

import static io.camunda.zeebe.util.buffer.BufferUtil.bufferAsString;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.camunda.zeebe.msgpack.pojo.Decoding;
import io.camunda.zeebe.msgpack.pojo.MsgpackBufferValue;
import io.camunda.zeebe.msgpack.pojo.MsgpackCodec;
import io.camunda.zeebe.msgpack.pojo.MsgpackOps;
import io.camunda.zeebe.msgpack.pojo.MsgpackPojo;
import io.camunda.zeebe.msgpack.pojo.MsgpackProperty;
import io.camunda.zeebe.msgpack.pojo.MsgpackUnknownProperties;
import io.camunda.zeebe.msgpack.pojo.UnknownProperties;
import io.camunda.zeebe.msgpack.spec.MsgPackHelper;
import io.camunda.zeebe.msgpack.value.DocumentValue;
import io.camunda.zeebe.protocol.impl.encoding.MsgPackConverter;
import io.camunda.zeebe.protocol.impl.record.MsgpackRecordValue;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.JobKind;
import io.camunda.zeebe.protocol.record.value.JobListenerEventType;
import io.camunda.zeebe.protocol.record.value.JobRecordValue;
import io.camunda.zeebe.protocol.record.value.JobRecordValue.JobSecretReferenceValue;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.jspecify.annotations.NullMarked;

@MsgpackPojo
@NullMarked
public final class JobRecord extends MsgpackBufferValue<JobRecord>
    implements MsgpackRecordValue, JobRecordValue {

  /**
   * The worker type prefix for Camunda Agentic AI jobs that are set in Camunda Connectors.
   *
   * <p>For 8.9 this is accepted as limitation that all agentic jobs must have this prefix in order
   * to be categorized as agentic jobs.
   *
   * <p>In the future, we might want to introduce a more robust way of identifying agentic jobs
   */
  public static final String IO_CAMUNDA_AI_AGENT_JOB_WORKER_TYPE_PREFIX =
      "io.camunda.agenticai:aiagent";

  /**
   * Sentinel lease token that does not scope to a specific job activation, meaning "apply to every
   * activation of this job". Used to signal "the whole job is gone" — e.g. when discarding agent
   * history for a destroyed job, an empty lease discards all items for the job regardless of which
   * activation produced them.
   */
  public static final String EMPTY_JOB_LEASE_TOKEN = "";

  public static final DirectBuffer NO_HEADERS = new UnsafeBuffer(MsgPackHelper.EMTPY_OBJECT);
  public static final String RETRIES = "retries";
  public static final String TIMEOUT = "timeout";
  public static final String PRIORITY = "priority";

  long deadline = -1;
  long timeout = -1;
  DirectBuffer worker = Decoding.EMPTY;
  int retries = -1;
  long retryBackoff = 0;
  long recurringTime = -1;
  DirectBuffer type = Decoding.EMPTY;

  @MsgpackProperty(raw = true)
  DirectBuffer customHeaders = NO_HEADERS;

  @MsgpackProperty(document = true)
  DirectBuffer variables = DocumentValue.EMPTY_DOCUMENT;

  DirectBuffer errorMessage = Decoding.EMPTY;
  DirectBuffer errorCode = Decoding.EMPTY;
  DirectBuffer bpmnProcessId = Decoding.EMPTY;
  int processDefinitionVersion = -1;
  long processDefinitionKey = -1L;
  long processInstanceKey = -1L;
  JobKind jobKind = JobKind.BPMN_ELEMENT;
  JobListenerEventType jobListenerEventType = JobListenerEventType.UNSPECIFIED;
  DirectBuffer elementId = Decoding.EMPTY;
  BpmnElementType elementType = BpmnElementType.UNSPECIFIED;
  long elementInstanceKey = -1L;
  DirectBuffer tenantId = BufferUtil.wrapString(TenantOwned.DEFAULT_TENANT_IDENTIFIER);
  final List<DirectBuffer> changedAttributes = new ArrayList<>();
  final JobResult result = new JobResult();
  final List<DirectBuffer> tags = new ArrayList<>();
  boolean isUserTaskMigration = false;
  long rootProcessInstanceKey = -1L;
  int storageOrdinal = 0;
  int priority = 0;
  DirectBuffer businessId = Decoding.EMPTY;
  DirectBuffer jobLeaseToken = Decoding.EMPTY;
  final List<JobSecretReference> secretReferences = new ArrayList<>();

  @MsgpackUnknownProperties final UnknownProperties unknownProperties = new UnknownProperties();

  public void wrapWithoutVariables(final JobRecord record) {
    deadline = record.getDeadline();
    timeout = record.getTimeout();
    worker = copy(record.worker);
    retries = record.getRetries();
    retryBackoff = record.getRetryBackoff();
    recurringTime = record.getRecurringTime();
    type = copy(record.type);
    customHeaders = copy(record.customHeaders);
    errorMessage = copy(record.errorMessage);
    errorCode = copy(record.errorCode);
    bpmnProcessId = copy(record.bpmnProcessId);
    processDefinitionVersion = record.getProcessDefinitionVersion();
    processDefinitionKey = record.getProcessDefinitionKey();
    processInstanceKey = record.getProcessInstanceKey();
    jobKind = record.getJobKind();
    jobListenerEventType = record.getJobListenerEventType();
    elementId = copy(record.elementId);
    elementType = record.getElementType();
    elementInstanceKey = record.getElementInstanceKey();
    tenantId = copy(record.tenantId);
    setChangedAttributes(record.getChangedAttributes());
    result.wrap(record.getResult());
    isUserTaskMigration = record.isJobToUserTaskMigration();

    setTags(record.getTags());
    rootProcessInstanceKey = record.getRootProcessInstanceKey();
    storageOrdinal = record.getStorageOrdinal();
    priority = record.getPriority();
    businessId = copy(record.businessId);
    jobLeaseToken = copy(record.jobLeaseToken);
    copySecretReferencesFrom(record);
  }

  private void copySecretReferencesFrom(final JobRecord record) {
    // same-class buffer copy avoids the String round-trip of
    // setSecretReferences(getSecretReferences())
    secretReferences.clear();
    record.secretReferences.forEach(
        reference -> {
          final var copy = new JobSecretReference();
          copy.copyFrom(reference);
          secretReferences.add(copy);
        });
  }

  public void wrap(final JobRecord record) {
    wrapWithoutVariables(record);
    variables = copy(record.variables);
  }

  public JobRecord resetVariables() {
    variables = DocumentValue.EMPTY_DOCUMENT;
    return this;
  }

  @JsonIgnore
  public DirectBuffer getCustomHeadersBuffer() {
    return customHeaders;
  }

  @JsonIgnore
  public DirectBuffer getErrorMessageBuffer() {
    return errorMessage;
  }

  @JsonIgnore
  public DirectBuffer getErrorCodeBuffer() {
    return errorCode;
  }

  @Override
  public String getType() {
    return bufferAsString(type);
  }

  @Override
  public Map<String, String> getCustomHeaders() {
    return MsgPackConverter.convertToStringMap(customHeaders);
  }

  @Override
  public String getWorker() {
    return bufferAsString(worker);
  }

  @Override
  public int getRetries() {
    return retries;
  }

  @Override
  public int getPriority() {
    return priority;
  }

  @Override
  public long getRetryBackoff() {
    return retryBackoff;
  }

  @Override
  public long getRecurringTime() {
    return recurringTime;
  }

  @Override
  public long getDeadline() {
    return deadline;
  }

  @Override
  public long getTimeout() {
    return timeout;
  }

  @Override
  public String getErrorMessage() {
    return bufferAsString(errorMessage);
  }

  @Override
  public String getErrorCode() {
    return bufferAsString(errorCode);
  }

  @Override
  public String getElementId() {
    return bufferAsString(elementId);
  }

  @Override
  public BpmnElementType getElementType() {
    return elementType;
  }

  @Override
  public long getElementInstanceKey() {
    return elementInstanceKey;
  }

  @Override
  public long getRootProcessInstanceKey() {
    return rootProcessInstanceKey;
  }

  public JobRecord setRootProcessInstanceKey(final long rootProcessInstanceKey) {
    this.rootProcessInstanceKey = rootProcessInstanceKey;
    return this;
  }

  @Override
  public String getBusinessId() {
    return bufferAsString(businessId);
  }

  public JobRecord setBusinessId(final String businessId) {
    this.businessId = BufferUtil.wrapString(businessId);
    return this;
  }

  public JobRecord setBusinessId(final DirectBuffer businessId) {
    this.businessId = copy(businessId);
    return this;
  }

  @Override
  public String getJobLeaseToken() {
    return bufferAsString(jobLeaseToken);
  }

  @Override
  public String getBpmnProcessId() {
    return bufferAsString(bpmnProcessId);
  }

  @Override
  public int getProcessDefinitionVersion() {
    return processDefinitionVersion;
  }

  @Override
  public long getProcessDefinitionKey() {
    return processDefinitionKey;
  }

  public JobRecord setProcessDefinitionKey(final long processDefinitionKey) {
    this.processDefinitionKey = processDefinitionKey;
    return this;
  }

  @Override
  public JobKind getJobKind() {
    return jobKind;
  }

  public JobRecord setJobKind(final JobKind jobKind) {
    this.jobKind = jobKind;
    return this;
  }

  @Override
  public JobListenerEventType getJobListenerEventType() {
    return jobListenerEventType;
  }

  @Override
  public Set<String> getChangedAttributes() {
    return changedAttributes.stream().map(BufferUtil::bufferAsString).collect(Collectors.toSet());
  }

  public JobRecord setChangedAttributes(final Set<String> changedAttributes) {
    this.changedAttributes.clear();
    changedAttributes.forEach(
        attribute -> this.changedAttributes.add(BufferUtil.wrapString(attribute)));
    return this;
  }

  @Override
  public JobResult getResult() {
    return result;
  }

  public JobRecord setResult(final JobResult result) {
    if (result != null) {
      this.result.wrap(result);
    }
    return this;
  }

  @Override
  public Set<String> getTags() {
    return tags.stream().map(BufferUtil::bufferAsString).collect(Collectors.toSet());
  }

  public JobRecord setTags(final Set<String> tags) {
    this.tags.clear();
    if (tags != null) {
      tags.forEach(tag -> this.tags.add(BufferUtil.wrapString(tag)));
    }
    return this;
  }

  @Override
  public boolean isJobToUserTaskMigration() {
    return isUserTaskMigration;
  }

  @Override
  public List<JobSecretReferenceValue> getSecretReferences() {
    // detach copies so the returned list stays valid if this record is reused or reset later
    return secretReferences.stream()
        .map(
            element -> {
              final var copy = new JobSecretReference();
              copy.copy(element);
              return (JobSecretReferenceValue) copy;
            })
        .toList();
  }

  public JobRecord setSecretReferences(
      final List<? extends JobSecretReferenceValue> secretReferences) {
    this.secretReferences.clear();
    if (secretReferences != null) {
      secretReferences.forEach(
          reference ->
              addSecretReference(
                  reference.getStoreId(), reference.getSecretReference(), reference.getPath()));
    }
    return this;
  }

  public JobRecord setProcessDefinitionVersion(final int version) {
    processDefinitionVersion = version;
    return this;
  }

  public JobRecord setBpmnProcessId(final String bpmnProcessId) {
    this.bpmnProcessId = BufferUtil.wrapString(bpmnProcessId);
    return this;
  }

  public JobRecord setBpmnProcessId(final DirectBuffer bpmnProcessId) {
    this.bpmnProcessId = copy(bpmnProcessId);
    return this;
  }

  public JobRecord setJobLeaseToken(final String jobLeaseToken) {
    this.jobLeaseToken = BufferUtil.wrapString(jobLeaseToken);
    return this;
  }

  public JobRecord setElementInstanceKey(final long elementInstanceKey) {
    this.elementInstanceKey = elementInstanceKey;
    return this;
  }

  public JobRecord setElementType(final BpmnElementType elementType) {
    this.elementType = elementType;
    return this;
  }

  public JobRecord setElementId(final String elementId) {
    this.elementId = BufferUtil.wrapString(elementId);
    return this;
  }

  public JobRecord setElementId(final DirectBuffer elementId) {
    return setElementId(elementId, 0, elementId.capacity());
  }

  public JobRecord setErrorCode(final DirectBuffer errorCode) {
    this.errorCode = copy(errorCode);
    return this;
  }

  public JobRecord setErrorMessage(final String errorMessage) {
    this.errorMessage = BufferUtil.wrapString(errorMessage);
    return this;
  }

  public JobRecord setErrorMessage(final DirectBuffer buf) {
    return setErrorMessage(buf, 0, buf.capacity());
  }

  public JobRecord setTimeout(final long val) {
    timeout = val;
    return this;
  }

  public JobRecord setDeadline(final long val) {
    deadline = val;
    return this;
  }

  public JobRecord setRecurringTime(final long recurringTime) {
    this.recurringTime = recurringTime;
    return this;
  }

  public JobRecord setRetryBackoff(final long retryBackoff) {
    this.retryBackoff = retryBackoff;
    return this;
  }

  public JobRecord setPriority(final int priority) {
    this.priority = priority;
    return this;
  }

  public JobRecord setRetries(final int retries) {
    this.retries = retries;
    return this;
  }

  public JobRecord setWorker(final String worker) {
    this.worker = BufferUtil.wrapString(worker);
    return this;
  }

  public JobRecord setWorker(final DirectBuffer worker) {
    return setWorker(worker, 0, worker.capacity());
  }

  public JobRecord setCustomHeaders(final DirectBuffer buffer) {
    customHeaders = copy(buffer);
    return this;
  }

  public JobRecord setType(final String type) {
    this.type = BufferUtil.wrapString(type);
    return this;
  }

  public JobRecord setType(final DirectBuffer buf) {
    return setType(buf, 0, buf.capacity());
  }

  /**
   * Direct access to the secret reference elements, without the detached copies of {@link
   * #getSecretReferences()}. The elements stay owned by this record; do not hold on to them.
   */
  @JsonIgnore
  public List<JobSecretReference> secretReferences() {
    return Collections.unmodifiableList(secretReferences);
  }

  @JsonIgnore
  public boolean hasSecretReferences() {
    return !secretReferences.isEmpty();
  }

  public JobRecord addSecretReference(
      final String storeId, final String secretReference, final String path) {
    secretReferences.add(
        new JobSecretReference()
            .setStoreId(storeId)
            .setSecretReference(secretReference)
            .setPath(path));
    return this;
  }

  public JobRecord resetSecretReferences() {
    secretReferences.clear();
    return this;
  }

  @JsonIgnore
  public DirectBuffer getBusinessIdBuffer() {
    return businessId;
  }

  @JsonIgnore
  public boolean hasJobLeaseToken() {
    return !getJobLeaseToken().isEmpty();
  }

  @JsonIgnore
  public DirectBuffer getJobLeaseTokenBuffer() {
    return jobLeaseToken;
  }

  @Override
  public int getStorageOrdinal() {
    return storageOrdinal;
  }

  public JobRecord setStorageOrdinal(final int storageOrdinal) {
    this.storageOrdinal = storageOrdinal;
    return this;
  }

  public JobRecord setListenerEventType(final JobListenerEventType jobListenerEventType) {
    this.jobListenerEventType = jobListenerEventType;
    return this;
  }

  public JobRecord setIsJobToUserTaskMigration(final boolean isJobToUserTaskMigration) {
    isUserTaskMigration = isJobToUserTaskMigration;
    return this;
  }

  @JsonIgnore
  public Map<String, Object> getCustomHeadersObjectMap() {
    return MsgPackConverter.convertToMap(customHeaders);
  }

  @JsonIgnore
  public DirectBuffer getTypeBuffer() {
    return type;
  }

  @Override
  public Map<String, Object> getVariables() {
    return MsgPackConverter.convertToMap(variables);
  }

  public JobRecord setVariables(final DirectBuffer variables) {
    this.variables = copy(Decoding.document(variables));
    return this;
  }

  @JsonIgnore
  public DirectBuffer getVariablesBuffer() {
    return variables;
  }

  @JsonIgnore
  public DirectBuffer getWorkerBuffer() {
    return worker;
  }

  @JsonIgnore
  public DirectBuffer getBpmnProcessIdBuffer() {
    return bpmnProcessId;
  }

  @Override
  public long getProcessInstanceKey() {
    return processInstanceKey;
  }

  public JobRecord setProcessInstanceKey(final long key) {
    processInstanceKey = key;
    return this;
  }

  @JsonIgnore
  public DirectBuffer getElementIdBuffer() {
    return elementId;
  }

  public JobRecord setElementId(final DirectBuffer activityId, final int offset, final int length) {
    elementId = copy(activityId, offset, length);
    return this;
  }

  public JobRecord setErrorMessage(final DirectBuffer buf, final int offset, final int length) {
    errorMessage = copy(buf, offset, length);
    return this;
  }

  public JobRecord setType(final DirectBuffer buf, final int offset, final int length) {
    type = copy(buf, offset, length);
    return this;
  }

  public JobRecord setWorker(final DirectBuffer worker, final int offset, final int length) {
    this.worker = copy(worker, offset, length);
    return this;
  }

  @Override
  public String getTenantId() {
    return bufferAsString(tenantId);
  }

  public JobRecord setTenantId(final String tenantId) {
    this.tenantId = BufferUtil.wrapString(tenantId);
    return this;
  }

  /**
   * @deprecated This job-type-prefix check now only backs the 8.9 compatibility half of audit
   *     attribution (jobs deployed on 8.9 with this job type and no resolved agent definition still
   *     need attribution). Every other agentic decision uses whether the job's element has a
   *     resolved agent definition instead. Remove once no supported version relies on this prefix
   *     rule anymore, and affected processes have been redeployed with changed content. Tracked in
   *     https://github.com/camunda/camunda/issues/60860.
   */
  @Deprecated
  @JsonIgnore
  public boolean hasAgenticPrefix() {
    return getType().startsWith(IO_CAMUNDA_AI_AGENT_JOB_WORKER_TYPE_PREFIX);
  }

  // setters detach the value: callers hand in buffers they reuse afterwards
  private static DirectBuffer copy(final DirectBuffer source) {
    return copy(source, 0, source.capacity());
  }

  private static DirectBuffer copy(final DirectBuffer source, final int offset, final int length) {
    final var bytes = new byte[length];
    source.getBytes(offset, bytes);
    return new UnsafeBuffer(bytes);
  }

  @Override
  protected MsgpackCodec<JobRecord> codec() {
    return JobRecordCodec.INSTANCE;
  }

  @Override
  protected MsgpackOps<JobRecord> ops() {
    return JobRecordOps.INSTANCE;
  }
}
