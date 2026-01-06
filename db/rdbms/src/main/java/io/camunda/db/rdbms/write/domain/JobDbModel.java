/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.write.domain;

import io.camunda.db.rdbms.write.util.CustomHeaderSerializer;
import io.camunda.db.rdbms.write.util.TruncateUtil;
import io.camunda.search.entities.JobEntity.JobKind;
import io.camunda.search.entities.JobEntity.JobState;
import io.camunda.search.entities.JobEntity.ListenerEventType;
import io.camunda.util.ObjectBuilder;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JobDbModel implements Copyable<JobDbModel> {
  private static final Logger LOG = LoggerFactory.getLogger(JobDbModel.class);

  private Long jobKey;
  private String type;
  private String worker;
  private JobState state;
  private JobKind kind;
  private ListenerEventType listenerEventType;
  private Integer retries;
  private Boolean isDenied;
  private String deniedReason;
  private Boolean hasFailedWithRetriesLeft;
  private String errorCode;
  private String errorMessage;
  private String serializedCustomHeaders;
  private Map<String, String> customHeaders;
  private OffsetDateTime deadline;
  private OffsetDateTime endTime;
  private String processDefinitionId;
  private Long processDefinitionKey;
  private Long processInstanceKey;
  private String elementId;
  private Long elementInstanceKey;
  private String tenantId;
  private int partitionId;
  private OffsetDateTime historyCleanupDate;

  public JobDbModel(final Long jobKey) {
    this.jobKey = jobKey;
  }

  public JobDbModel(
      final Long jobKey,
      final String type,
      final String worker,
      final JobState state,
      final JobKind kind,
      final ListenerEventType listenerEventType,
      final Integer retries,
      final Boolean isDenied,
      final String deniedReason,
      final Boolean hasFailedWithRetriesLeft,
      final String errorCode,
      final String errorMessage,
      final Map<String, String> customHeaders,
      final OffsetDateTime deadline,
      final OffsetDateTime endTime,
      final String processDefinitionId,
      final Long processDefinitionKey,
      final Long processInstanceKey,
      final String elementId,
      final Long elementInstanceKey,
      final String tenantId,
      final int partitionId,
      final OffsetDateTime historyCleanupDate) {
    this.jobKey = jobKey;
    this.type = type;
    this.worker = worker;
    this.state = state;
    this.kind = kind;
    this.listenerEventType = listenerEventType;
    this.retries = retries;
    this.isDenied = isDenied;
    this.deniedReason = deniedReason;
    this.hasFailedWithRetriesLeft = hasFailedWithRetriesLeft;
    this.errorCode = errorCode;
    this.errorMessage = errorMessage;
    serializedCustomHeaders = CustomHeaderSerializer.serialize(customHeaders);
    this.customHeaders = customHeaders;
    this.deadline = deadline;
    this.endTime = endTime;
    this.processDefinitionId = processDefinitionId;
    this.processDefinitionKey = processDefinitionKey;
    this.processInstanceKey = processInstanceKey;
    this.elementId = elementId;
    this.elementInstanceKey = elementInstanceKey;
    this.tenantId = tenantId;
    this.partitionId = partitionId;
    this.historyCleanupDate = historyCleanupDate;
  }

  @Override
  public JobDbModel copy(
      final Function<ObjectBuilder<JobDbModel>, ObjectBuilder<JobDbModel>> copyFunction) {
    return copyFunction.apply(toBuilder()).build();
  }

  public JobDbModel truncateErrorMessage(final int sizeLimit, final Integer byteLimit) {
    if (errorMessage == null) {
      return this;
    }

    final var truncatedValue = TruncateUtil.truncateValue(errorMessage, sizeLimit, byteLimit);

    if (truncatedValue.length() < errorMessage.length()) {
      LOG.warn(
          "Truncated error message for job {}, original message was: {}", jobKey, errorMessage);
    }

    return new JobDbModel(
        jobKey,
        type,
        worker,
        state,
        kind,
        listenerEventType,
        retries,
        isDenied,
        deniedReason,
        hasFailedWithRetriesLeft,
        errorCode,
        truncatedValue,
        customHeaders,
        deadline,
        endTime,
        processDefinitionId,
        processDefinitionKey,
        processInstanceKey,
        elementId,
        elementInstanceKey,
        tenantId,
        partitionId,
        historyCleanupDate);
  }

  public Long jobKey() {
    return jobKey;
  }

  public void jobKey(final Long jobKey) {
    this.jobKey = jobKey;
  }

  public String type() {
    return type;
  }

  public void type(final String type) {
    this.type = type;
  }

  public String worker() {
    return worker;
  }

  public void worker(final String worker) {
    this.worker = worker;
  }

  public JobState state() {
    return state;
  }

  public void state(final JobState state) {
    this.state = state;
  }

  public JobKind kind() {
    return kind;
  }

  public void kind(final JobKind kind) {
    this.kind = kind;
  }

  public ListenerEventType listenerEventType() {
    return listenerEventType;
  }

  public void listenerEventType(final ListenerEventType listenerEventType) {
    this.listenerEventType = listenerEventType;
  }

  public Integer retries() {
    return retries;
  }

  public void retries(final Integer retries) {
    this.retries = retries;
  }

  public Boolean isDenied() {
    return isDenied;
  }

  public void isDenied(final Boolean isDenied) {
    this.isDenied = isDenied;
  }

  public String deniedReason() {
    return deniedReason;
  }

  public void deniedReason(final String deniedReason) {
    this.deniedReason = deniedReason;
  }

  public Boolean hasFailedWithRetriesLeft() {
    return hasFailedWithRetriesLeft;
  }

  public void hasFailedWithRetriesLeft(final Boolean hasFailedWithRetriesLeft) {
    this.hasFailedWithRetriesLeft = hasFailedWithRetriesLeft;
  }

  public String errorCode() {
    return errorCode;
  }

  public void errorCode(final String errorCode) {
    this.errorCode = errorCode;
  }

  public String errorMessage() {
    return errorMessage;
  }

  public void errorMessage(final String errorMessage) {
    this.errorMessage = errorMessage;
  }

  public String serializedCustomHeaders() {
    return serializedCustomHeaders;
  }

  public void setSerializedCustomHeaders(final String serializedCustomHeaders) {
    this.serializedCustomHeaders = serializedCustomHeaders;
    customHeaders = CustomHeaderSerializer.deserialize(serializedCustomHeaders);
  }

  public Map<String, String> customHeaders() {
    return customHeaders;
  }

  public OffsetDateTime deadline() {
    return deadline;
  }

  public void deadline(final OffsetDateTime deadline) {
    this.deadline = deadline;
  }

  public OffsetDateTime endTime() {
    return endTime;
  }

  public void endTime(final OffsetDateTime endTime) {
    this.endTime = endTime;
  }

  public String processDefinitionId() {
    return processDefinitionId;
  }

  public void processDefinitionId(final String processDefinitionId) {
    this.processDefinitionId = processDefinitionId;
  }

  public Long processDefinitionKey() {
    return processDefinitionKey;
  }

  public void processDefinitionKey(final Long processDefinitionKey) {
    this.processDefinitionKey = processDefinitionKey;
  }

  public Long processInstanceKey() {
    return processInstanceKey;
  }

  public void processInstanceKey(final Long processInstanceKey) {
    this.processInstanceKey = processInstanceKey;
  }

  public String elementId() {
    return elementId;
  }

  public void elementId(final String elementId) {
    this.elementId = elementId;
  }

  public Long elementInstanceKey() {
    return elementInstanceKey;
  }

  public void elementInstanceKey(final Long elementInstanceKey) {
    this.elementInstanceKey = elementInstanceKey;
  }

  public String tenantId() {
    return tenantId;
  }

  public void tenantId(final String tenantId) {
    this.tenantId = tenantId;
  }

  public int partitionId() {
    return partitionId;
  }

  public void partitionId(final int partitionId) {
    this.partitionId = partitionId;
  }

  public OffsetDateTime historyCleanupDate() {
    return historyCleanupDate;
  }

  public void historyCleanupDate(final OffsetDateTime historyCleanupDate) {
    this.historyCleanupDate = historyCleanupDate;
  }

  public ObjectBuilder<JobDbModel> toBuilder() {
    return new Builder()
        .jobKey(jobKey)
        .type(type)
        .worker(worker)
        .state(state)
        .kind(kind)
        .listenerEventType(listenerEventType)
        .retries(retries)
        .isDenied(isDenied)
        .deniedReason(deniedReason)
        .hasFailedWithRetriesLeft(hasFailedWithRetriesLeft)
        .errorCode(errorCode)
        .errorMessage(errorMessage)
        .customHeaders(customHeaders)
        .deadline(deadline)
        .endTime(endTime)
        .processDefinitionId(processDefinitionId)
        .processDefinitionKey(processDefinitionKey)
        .processInstanceKey(processInstanceKey)
        .elementId(elementId)
        .elementInstanceKey(elementInstanceKey)
        .tenantId(tenantId)
        .partitionId(partitionId)
        .historyCleanupDate(historyCleanupDate);
  }

  public static class Builder implements ObjectBuilder<JobDbModel> {

    private Long jobKey;
    private String type;
    private String worker;
    private JobState state;
    private JobKind kind;
    private ListenerEventType listenerEventType;
    private Integer retries;
    private Boolean isDenied;
    private String deniedReason;
    private Boolean hasFailedWithRetriesLeft;
    private String errorCode;
    private String errorMessage;
    private Map<String, String> customHeaders;
    private OffsetDateTime deadline;
    private OffsetDateTime endTime;
    private String processDefinitionId;
    private Long processDefinitionKey;
    private Long processInstanceKey;
    private String elementId;
    private Long elementInstanceKey;
    private String tenantId;
    private int partitionId;
    private OffsetDateTime historyCleanupDate;

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

    public Builder customHeaders(final Map<String, String> customHeaders) {
      this.customHeaders = customHeaders;
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

    public Builder partitionId(final int partitionId) {
      this.partitionId = partitionId;
      return this;
    }

    public Builder historyCleanupDate(final OffsetDateTime value) {
      historyCleanupDate = value;
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
          isDenied,
          deniedReason,
          hasFailedWithRetriesLeft,
          errorCode,
          errorMessage,
          customHeaders,
          deadline,
          endTime,
          processDefinitionId,
          processDefinitionKey,
          processInstanceKey,
          elementId,
          elementInstanceKey,
          tenantId,
          partitionId,
          historyCleanupDate);
    }
  }
}
