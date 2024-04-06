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
package io.camunda.tasklist.entities.meta;

import io.camunda.tasklist.entities.TasklistEntity;
import java.util.Objects;

public class ImportPositionEntity extends TasklistEntity<ImportPositionEntity> {

  private String aliasName;

  private int partitionId;

  private long position;

  private long sequence;

  private String indexName;

  public ImportPositionEntity() {}

  public String getAliasName() {
    return aliasName;
  }

  public ImportPositionEntity setAliasName(final String aliasName) {
    this.aliasName = aliasName;
    return this;
  }

  public int getPartitionId() {
    return partitionId;
  }

  public ImportPositionEntity setPartitionId(final int partitionId) {
    this.partitionId = partitionId;
    return this;
  }

  public long getSequence() {
    return sequence;
  }

  public ImportPositionEntity setSequence(final long sequence) {
    this.sequence = sequence;
    return this;
  }

  public long getPosition() {
    return position;
  }

  public ImportPositionEntity setPosition(final long position) {
    this.position = position;
    return this;
  }

  public String getIndexName() {
    return indexName;
  }

  public ImportPositionEntity setIndexName(final String indexName) {
    this.indexName = indexName;
    return this;
  }

  @Override
  public String getId() {
    return String.format("%s-%s", partitionId, aliasName);
  }

  public static ImportPositionEntity createFrom(
      final long sequence,
      ImportPositionEntity importPositionEntity,
      long newPosition,
      String indexName) {
    return new ImportPositionEntity()
        .setSequence(sequence)
        .setAliasName(importPositionEntity.getAliasName())
        .setPartitionId(importPositionEntity.getPartitionId())
        .setIndexName(indexName)
        .setPosition(newPosition);
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
    final ImportPositionEntity that = (ImportPositionEntity) o;
    return partitionId == that.partitionId
        && position == that.position
        && sequence == that.sequence
        && Objects.equals(aliasName, that.aliasName)
        && Objects.equals(indexName, that.indexName);
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), sequence, aliasName, partitionId, position, indexName);
  }

  @Override
  public String toString() {
    return "ImportPositionEntity{"
        + "aliasName='"
        + aliasName
        + '\''
        + ", sequence="
        + sequence
        + ", partitionId="
        + partitionId
        + ", position="
        + position
        + ", indexName='"
        + indexName
        + '\''
        + '}';
  }
}
