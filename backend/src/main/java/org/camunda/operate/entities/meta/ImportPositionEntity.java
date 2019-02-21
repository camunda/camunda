/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.entities.meta;

public class ImportPositionEntity {

  private String aliasName;

  private int partitionId;

  private long position;

  public ImportPositionEntity() {
  }

  public ImportPositionEntity(String aliasName, int partitionId, long position) {
    this.aliasName = aliasName;
    this.partitionId = partitionId;
    this.position = position;
  }

  public String getAliasName() {
    return aliasName;
  }

  public void setAliasName(String aliasName) {
    this.aliasName = aliasName;
  }

  public int getPartitionId() {
    return partitionId;
  }

  public void setPartitionId(int partitionId) {
    this.partitionId = partitionId;
  }

  public long getPosition() {
    return position;
  }

  public void setPosition(long position) {
    this.position = position;
  }

  public String getId() {
    return String.format("%s-%s", partitionId, aliasName);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o)
      return true;
    if (o == null || getClass() != o.getClass())
      return false;

    ImportPositionEntity that = (ImportPositionEntity) o;

    if (partitionId != that.partitionId)
      return false;
    if (position != that.position)
      return false;
    return aliasName != null ? aliasName.equals(that.aliasName) : that.aliasName == null;
  }

  @Override
  public int hashCode() {
    int result = aliasName != null ? aliasName.hashCode() : 0;
    result = 31 * result + partitionId;
    result = 31 * result + (int) (position ^ (position >>> 32));
    return result;
  }
}
