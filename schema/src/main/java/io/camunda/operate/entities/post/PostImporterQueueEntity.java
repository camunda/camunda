/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.entities.post;

import io.camunda.operate.entities.OperateEntity;

import java.time.OffsetDateTime;
import java.util.Objects;

public class PostImporterQueueEntity extends OperateEntity<PostImporterQueueEntity> {

  private Long key;

  private PostImporterActionType actionType;

  private String intent;

  private OffsetDateTime creationTime;

  private Integer partitionId;

  private Long processInstanceKey;

  private Long position;

  public Long getKey() {
    return key;
  }

  public PostImporterQueueEntity setKey(Long key) {
    this.key = key;
    return this;
  }

  public PostImporterActionType getActionType() {
    return actionType;
  }

  public PostImporterQueueEntity setActionType(PostImporterActionType actionType) {
    this.actionType = actionType;
    return this;
  }

  public String getIntent() {
    return intent;
  }

  public PostImporterQueueEntity setIntent(String intent) {
    this.intent = intent;
    return this;
  }

  public OffsetDateTime getCreationTime() {
    return creationTime;
  }

  public PostImporterQueueEntity setCreationTime(OffsetDateTime creationTime) {
    this.creationTime = creationTime;
    return this;
  }

  public Integer getPartitionId() {
    return partitionId;
  }

  public PostImporterQueueEntity setPartitionId(Integer partitionId) {
    this.partitionId = partitionId;
    return this;
  }

  public Long getProcessInstanceKey() {
    return processInstanceKey;
  }

  public PostImporterQueueEntity setProcessInstanceKey(Long processInstanceKey) {
    this.processInstanceKey = processInstanceKey;
    return this;
  }

  public Long getPosition() {
    return position;
  }

  public PostImporterQueueEntity setPosition(Long position) {
    this.position = position;
    return this;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o)
      return true;
    if (o == null || getClass() != o.getClass())
      return false;
    if (!super.equals(o))
      return false;
    PostImporterQueueEntity that = (PostImporterQueueEntity) o;
    return Objects.equals(key, that.key) && actionType == that.actionType && Objects.equals(intent,
        that.intent) && Objects.equals(creationTime, that.creationTime) && Objects.equals(partitionId,
        that.partitionId) && Objects.equals(processInstanceKey, that.processInstanceKey) && Objects.equals(position,
        that.position);
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), key, actionType, intent, creationTime, partitionId, processInstanceKey,
        position);
  }
}
