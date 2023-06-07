/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.entities.meta;

import io.camunda.operate.entities.OperateEntity;

import java.util.Objects;

public class ImportPositionEntity extends OperateEntity<ImportPositionEntity> {

  private String aliasName;

  private int partitionId;

  private long position;

  private long sequence;

  private Long postImporterPosition;

  private String indexName;

  public String getAliasName() {
    return aliasName;
  }

  public ImportPositionEntity setAliasName(String aliasName) {
    this.aliasName = aliasName;
    return this;
  }

  public int getPartitionId() {
    return partitionId;
  }

  public ImportPositionEntity setPartitionId(int partitionId) {
    this.partitionId = partitionId;
    return this;
  }

  public long getPosition() {
    return position;
  }

  public ImportPositionEntity setPosition(long position) {
    this.position = position;
    return this;
  }

  public long getSequence(){
    return sequence;
  }

  public ImportPositionEntity setSequence(final long sequence){
    this.sequence = sequence;
    return this;
  }

  public Long getPostImporterPosition() {
    return postImporterPosition;
  }

  public ImportPositionEntity setPostImporterPosition(Long postImporterPosition) {
    this.postImporterPosition = postImporterPosition;
    return this;
  }

  public String getIndexName() {
    return indexName;
  }

  public ImportPositionEntity setIndexName(String indexName) {
    this.indexName = indexName;
    return this;
  }

  public String getId() {
    return String.format("%s-%s", partitionId, aliasName);
  }

  public static ImportPositionEntity createFrom(final long sequence, ImportPositionEntity importPositionEntity, long newPosition, String indexName) {
    return new ImportPositionEntity()
        .setSequence(sequence)
        .setAliasName(importPositionEntity.getAliasName())
        .setPartitionId(importPositionEntity.getPartitionId())
        .setIndexName(indexName)
        .setPosition(newPosition);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o)
      return true;
    if (o == null || getClass() != o.getClass())
      return false;
    if (!super.equals(o))
      return false;
    ImportPositionEntity that = (ImportPositionEntity) o;
    return partitionId == that.partitionId && position == that.position && sequence == that.sequence && Objects.equals(
        aliasName, that.aliasName) && Objects.equals(postImporterPosition, that.postImporterPosition) && Objects.equals(
        indexName, that.indexName);
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), aliasName, partitionId, position, sequence, postImporterPosition, indexName);
  }

  @Override
  public String toString() {
    return "ImportPositionEntity{" + "aliasName='" + aliasName + '\'' + ", partitionId=" + partitionId + ", position=" + position + ", sequence=" + sequence + ", postImporterSequence=" + postImporterPosition + ", indexName='" + indexName + '\'' + '}';
  }
}
