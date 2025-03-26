/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.webapps.schema.entities;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Objects;

public class ImportPositionEntity
    implements ExporterEntity<ImportPositionEntity>, PartitionedEntity<ImportPositionEntity> {

  private String id;

  private String aliasName;

  private int partitionId;

  private long position;

  private long sequence;

  @JsonInclude(JsonInclude.Include.NON_NULL)
  private Long postImporterPosition;

  private String indexName;

  private boolean completed;

  public static ImportPositionEntity createFrom(
      final long sequence,
      final ImportPositionEntity importPositionEntity,
      final long newPosition,
      final String indexName,
      final boolean completed) {
    return new ImportPositionEntity()
        .setSequence(sequence)
        .setAliasName(importPositionEntity.getAliasName())
        .setPartitionId(importPositionEntity.getPartitionId())
        .setIndexName(indexName)
        .setPosition(newPosition)
        .setCompleted(completed);
  }

  public String getAliasName() {
    return aliasName;
  }

  public ImportPositionEntity setAliasName(final String aliasName) {
    this.aliasName = aliasName;
    return this;
  }

  @Override
  public int getPartitionId() {
    return partitionId;
  }

  @Override
  public ImportPositionEntity setPartitionId(final int partitionId) {
    this.partitionId = partitionId;
    return this;
  }

  public long getPosition() {
    return position;
  }

  public ImportPositionEntity setPosition(final long position) {
    this.position = position;
    return this;
  }

  public long getSequence() {
    return sequence;
  }

  public ImportPositionEntity setSequence(final long sequence) {
    this.sequence = sequence;
    return this;
  }

  public Long getPostImporterPosition() {
    return postImporterPosition;
  }

  public ImportPositionEntity setPostImporterPosition(final Long postImporterPosition) {
    this.postImporterPosition = postImporterPosition;
    return this;
  }

  public String getIndexName() {
    return indexName;
  }

  public ImportPositionEntity setIndexName(final String indexName) {
    this.indexName = indexName;
    return this;
  }

  public boolean getCompleted() {
    return completed;
  }

  public ImportPositionEntity setCompleted(final boolean completed) {
    this.completed = completed;
    return this;
  }

  @Override
  public String getId() {
    return String.format("%s-%s", partitionId, aliasName);
  }

  @Override
  public ImportPositionEntity setId(final String id) {
    this.id = id;
    return this;
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        id, aliasName, partitionId, position, sequence, postImporterPosition, indexName);
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final ImportPositionEntity that = (ImportPositionEntity) o;
    return Objects.equals(id, that.id)
        && partitionId == that.partitionId
        && position == that.position
        && sequence == that.sequence
        && Objects.equals(aliasName, that.aliasName)
        && Objects.equals(postImporterPosition, that.postImporterPosition)
        && Objects.equals(indexName, that.indexName);
  }

  @Override
  public String toString() {
    return "ImportPositionEntity{"
        + "aliasName='"
        + aliasName
        + '\''
        + ", partitionId="
        + partitionId
        + ", position="
        + position
        + ", sequence="
        + sequence
        + ", postImporterSequence="
        + postImporterPosition
        + ", indexName='"
        + indexName
        + ", completed='"
        + completed
        + '\''
        + '}';
  }
}
