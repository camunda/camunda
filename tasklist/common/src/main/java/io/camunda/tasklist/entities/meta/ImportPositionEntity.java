/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
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
