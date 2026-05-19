/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.webapps.schema.entities;

public class ImportPositionEntity
    implements ExporterEntity<ImportPositionEntity>, PartitionedEntity<ImportPositionEntity> {

  private String id;
  private String aliasName;
  private int partitionId;
  private long position;
  private long sequence;
  private long postImporterPosition;
  private String indexName;
  private boolean completed;

  @Override
  public String getId() {
    return id;
  }

  @Override
  public ImportPositionEntity setId(final String id) {
    this.id = id;
    return this;
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

  public long getPostImporterPosition() {
    return postImporterPosition;
  }

  public ImportPositionEntity setPostImporterPosition(final long postImporterPosition) {
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

  public boolean isCompleted() {
    return completed;
  }

  public ImportPositionEntity setCompleted(final boolean completed) {
    this.completed = completed;
    return this;
  }
}
