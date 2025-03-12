/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.webapps.schema.entities.operate.post;

import io.camunda.webapps.schema.entities.ExporterEntity;
import java.time.OffsetDateTime;
import java.util.Objects;

public class PostImporterQueueEntity implements ExporterEntity<PostImporterQueueEntity> {

  private String id;

  private Long key;

  private PostImporterActionType actionType;

  private String intent;

  private OffsetDateTime creationTime;

  private Integer partitionId;

  private Long processInstanceKey;

  private Long position;

  @Override
  public String getId() {
    return id;
  }

  @Override
  public PostImporterQueueEntity setId(final String id) {
    this.id = id;
    return this;
  }

  public Long getKey() {
    return key;
  }

  public PostImporterQueueEntity setKey(final Long key) {
    this.key = key;
    return this;
  }

  public PostImporterActionType getActionType() {
    return actionType;
  }

  public PostImporterQueueEntity setActionType(final PostImporterActionType actionType) {
    this.actionType = actionType;
    return this;
  }

  public String getIntent() {
    return intent;
  }

  public PostImporterQueueEntity setIntent(final String intent) {
    this.intent = intent;
    return this;
  }

  public OffsetDateTime getCreationTime() {
    return creationTime;
  }

  public PostImporterQueueEntity setCreationTime(final OffsetDateTime creationTime) {
    this.creationTime = creationTime;
    return this;
  }

  public Integer getPartitionId() {
    return partitionId;
  }

  public PostImporterQueueEntity setPartitionId(final Integer partitionId) {
    this.partitionId = partitionId;
    return this;
  }

  public Long getProcessInstanceKey() {
    return processInstanceKey;
  }

  public PostImporterQueueEntity setProcessInstanceKey(final Long processInstanceKey) {
    this.processInstanceKey = processInstanceKey;
    return this;
  }

  public Long getPosition() {
    return position;
  }

  public PostImporterQueueEntity setPosition(final Long position) {
    this.position = position;
    return this;
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        id, key, actionType, intent, creationTime, partitionId, processInstanceKey, position);
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final PostImporterQueueEntity that = (PostImporterQueueEntity) o;
    return Objects.equals(id, that.id)
        && Objects.equals(key, that.key)
        && actionType == that.actionType
        && Objects.equals(intent, that.intent)
        && Objects.equals(creationTime, that.creationTime)
        && Objects.equals(partitionId, that.partitionId)
        && Objects.equals(processInstanceKey, that.processInstanceKey)
        && Objects.equals(position, that.position);
  }
}
