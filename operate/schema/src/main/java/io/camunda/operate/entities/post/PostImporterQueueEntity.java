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
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    if (!super.equals(o)) return false;
    PostImporterQueueEntity that = (PostImporterQueueEntity) o;
    return Objects.equals(key, that.key)
        && actionType == that.actionType
        && Objects.equals(intent, that.intent)
        && Objects.equals(creationTime, that.creationTime)
        && Objects.equals(partitionId, that.partitionId)
        && Objects.equals(processInstanceKey, that.processInstanceKey)
        && Objects.equals(position, that.position);
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        super.hashCode(),
        key,
        actionType,
        intent,
        creationTime,
        partitionId,
        processInstanceKey,
        position);
  }
}
