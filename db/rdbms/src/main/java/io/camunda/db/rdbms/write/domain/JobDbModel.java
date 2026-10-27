/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.write.domain;

import io.camunda.db.rdbms.write.util.MapSerializer;
import io.camunda.db.rdbms.write.util.TruncateUtil;
import io.camunda.search.entities.JobEntity.JobKind;
import io.camunda.search.entities.JobEntity.JobState;
import io.camunda.search.entities.JobEntity.ListenerEventType;
import io.camunda.util.ObjectBuilder;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public record JobDbModel(
    Long jobKey,
    String type,
    String worker,
    JobState state,
    JobKind kind,
    ListenerEventType listenerEventType,
    Integer retries,
    Integer priority,
    Boolean isDenied,
    String deniedReason,
    Boolean hasFailedWithRetriesLeft,
    String errorCode,
    String errorMessage,
    String serializedCustomHeaders,
    OffsetDateTime deadline,
    OffsetDateTime endTime,
    String processDefinitionId,
    Long processDefinitionKey,
    Long processInstanceKey,
    Long rootProcessInstanceKey,
    String businessId,
    String elementId,
    Long elementInstanceKey,
    String tenantId,
    int partitionId,
    OffsetDateTime creationTime,
    OffsetDateTime lastUpdateTime)
    implements Copyable<JobDbModel> {

  private static final Logger LOG = LoggerFactory.getLogger(JobDbModel.class);

  @Override
  public JobDbModel copy(
      final Function<ObjectBuilder<JobDbModel>, ObjectBuilder<JobDbModel>> copyFunction) {
    return copyFunction.apply(toBuilder()).build();
  }

  public JobDbModel truncateErrorMessage(final int sizeLimit, final Integer byteLimit) {
    final var truncatedValue = doTruncateErrorMessage(jobKey, errorMessage, sizeLimit, byteLimit);
    if (Objects.equals(truncatedValue, errorMessage)) {
      return this;
    }

    return new JobDbModel(
        jobKey,
        type,
        worker,
        state,
        kind,
        listenerEventType,
        retries,
        priority,
        isDenied,
        deniedReason,
        hasFailedWithRetriesLeft,
        errorCode,
        truncatedValue,
        serializedCustomHeaders,
        deadline,
        endTime,
        processDefinitionId,
        processDefinitionKey,
        processInstanceKey,
        rootProcessInstanceKey,
        businessId,
        elementId,
        elementInstanceKey,
        tenantId,
        partitionId,
        creationTime,
        lastUpdateTime);
  }

  private static String doTruncateErrorMessage(
      final Long jobKey, final String errorMessage, final int sizeLimit, final Integer byteLimit) {
    if (errorMessage == null) {
      return null;
    }
    final var truncatedValue = TruncateUtil.truncateValue(errorMessage, sizeLimit, byteLimit);
    if (truncatedValue.length() < errorMessage.length()) {
      LOG.warn(
          "Truncated error message for job {}, original message was: {}", jobKey, errorMessage);
    }
    return truncatedValue;
  }

  public Map<String, String> customHeaders() {
    return MapSerializer.deserialize(serializedCustomHeaders);
  }

  public Builder toBuilder() {
    return new Builder(serializedCustomHeaders)
        .jobKey(jobKey)
        .type(type)
        .worker(worker)
        .state(state)
        .kind(kind)
        .listenerEventType(listenerEventType)
        .retries(retries)
        .priority(priority)
        .isDenied(isDenied)
        .deniedReason(deniedReason)
        .hasFailedWithRetriesLeft(hasFailedWithRetriesLeft)
        .errorCode(errorCode)
        .errorMessage(errorMessage)
        .deadline(deadline)
        .endTime(endTime)
        .processDefinitionId(processDefinitionId)
        .processDefinitionKey(processDefinitionKey)
        .processInstanceKey(processInstanceKey)
        .rootProcessInstanceKey(rootProcessInstanceKey)
        .businessId(businessId)
        .elementId(elementId)
        .elementInstanceKey(elementInstanceKey)
        .tenantId(tenantId)
        .partitionId(partitionId)
        .creationTime(creationTime)
        .lastUpdateTime(lastUpdateTime);
  }

  public static class Builder implements ObjectBuilder<JobDbModel> {

    private Long jobKey;
    private String type;
    private String worker;
    private JobState state;
    private JobKind kind;
    private ListenerEventType listenerEventType;
    private Integer retries;
    private Integer priority;
    private Boolean isDenied;
    private String deniedReason;
    private Boolean hasFailedWithRetriesLeft = false;
    private String errorCode;
    private String errorMessage;
    private String serializedCustomHeaders;
    private OffsetDateTime deadline;
    private OffsetDateTime endTime;
    private String processDefinitionId;
    private Long processDefinitionKey;
    private Long processInstanceKey;
    private Long rootProcessInstanceKey;
    private String businessId;
    private String elementId;
    private Long elementInstanceKey;
    private String tenantId;
    private int partitionId;
    private OffsetDateTime creationTime;
    private OffsetDateTime lastUpdateTime;

    public Builder() {}

    // Seeds the raw serialized column value for toBuilder()/copy(), so a copy that never touches
    // customHeaders() carries the original string through unparsed instead of round-tripping it
    // through Jackson. Package-private, not a public setter: a second public setter aliasing the
    // same field as customHeaders(Map) would let callers silently drop one write (e.g.
    // builder.serializedCustomHeaders(x).customHeaders(y) loses x).
    Builder(final String serializedCustomHeaders) {
      this.serializedCustomHeaders = serializedCustomHeaders;
    }

    public Builder jobKey(final Long jobKey) {
      this.jobKey = jobKey;
      return this;
    }

    public Builder type(final String type) {
      this.type = type;
      return this;
    }

    public Builder worker(final String worker) {
      this.worker = worker;
      return this;
    }

    public Builder state(final JobState state) {
      this.state = state;
      return this;
    }

    public Builder kind(final JobKind kind) {
      this.kind = kind;
      return this;
    }

    public Builder listenerEventType(final ListenerEventType listenerEventType) {
      this.listenerEventType = listenerEventType;
      return this;
    }

    public Builder retries(final Integer retries) {
      this.retries = retries;
      return this;
    }

    public Builder priority(final Integer priority) {
      this.priority = priority;
      return this;
    }

    public Builder isDenied(final Boolean isDenied) {
      this.isDenied = isDenied;
      return this;
    }

    public Builder deniedReason(final String deniedReason) {
      this.deniedReason = deniedReason;
      return this;
    }

    public Builder hasFailedWithRetriesLeft(final Boolean hasFailedWithRetriesLeft) {
      this.hasFailedWithRetriesLeft = hasFailedWithRetriesLeft;
      return this;
    }

    public Builder errorCode(final String errorCode) {
      this.errorCode = errorCode;
      return this;
    }

    public Builder errorMessage(final String errorMessage) {
      this.errorMessage = errorMessage;
      return this;
    }

    public Builder truncateErrorMessage(final int sizeLimit, final Integer byteLimit) {
      errorMessage = doTruncateErrorMessage(jobKey, errorMessage, sizeLimit, byteLimit);
      return this;
    }

    public Builder customHeaders(final Map<String, String> customHeaders) {
      serializedCustomHeaders = MapSerializer.serialize(customHeaders);
      return this;
    }

    public Builder deadline(final OffsetDateTime deadline) {
      this.deadline = deadline;
      return this;
    }

    public Builder endTime(final OffsetDateTime endTime) {
      this.endTime = endTime;
      return this;
    }

    public Builder processDefinitionId(final String processDefinitionId) {
      this.processDefinitionId = processDefinitionId;
      return this;
    }

    public Builder processDefinitionKey(final Long processDefinitionKey) {
      this.processDefinitionKey = processDefinitionKey;
      return this;
    }

    public Builder elementId(final String elementId) {
      this.elementId = elementId;
      return this;
    }

    public Builder elementInstanceKey(final Long elementInstanceKey) {
      this.elementInstanceKey = elementInstanceKey;
      return this;
    }

    public Builder tenantId(final String tenantId) {
      this.tenantId = tenantId;
      return this;
    }

    public Builder processInstanceKey(final Long processInstanceKey) {
      this.processInstanceKey = processInstanceKey;
      return this;
    }

    public Builder rootProcessInstanceKey(final Long rootProcessInstanceKey) {
      this.rootProcessInstanceKey = rootProcessInstanceKey;
      return this;
    }

    public Builder businessId(final String businessId) {
      this.businessId = businessId;
      return this;
    }

    public Builder partitionId(final int partitionId) {
      this.partitionId = partitionId;
      return this;
    }

    public Builder creationTime(final OffsetDateTime value) {
      creationTime = value;
      return this;
    }

    public Builder lastUpdateTime(final OffsetDateTime value) {
      lastUpdateTime = value;
      return this;
    }

    @Override
    public JobDbModel build() {
      return new JobDbModel(
          jobKey,
          type,
          worker,
          state,
          kind,
          listenerEventType,
          retries,
          priority,
          isDenied,
          deniedReason,
          hasFailedWithRetriesLeft,
          errorCode,
          errorMessage,
          serializedCustomHeaders,
          deadline,
          endTime,
          processDefinitionId,
          processDefinitionKey,
          processInstanceKey,
          rootProcessInstanceKey,
          businessId,
          elementId,
          elementInstanceKey,
          tenantId,
          partitionId,
          creationTime,
          lastUpdateTime);
    }
  }
}
