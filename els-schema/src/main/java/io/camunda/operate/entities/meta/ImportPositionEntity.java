/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.entities.meta;

import io.camunda.operate.entities.OperateEntity;

public class ImportPositionEntity extends OperateEntity {

  private String aliasName;

  private int partitionId;

  private long position;

  private String indexName;

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

  public String getIndexName() {
    return indexName;
  }

  public void setIndexName(String indexName) {
    this.indexName = indexName;
  }

  public String getId() {
    return String.format("%s-%s", partitionId, aliasName);
  }

  public static ImportPositionEntity createFrom(ImportPositionEntity importPositionEntity, long newPosition, String indexName) {
    ImportPositionEntity newImportPositionEntity = new ImportPositionEntity();
    newImportPositionEntity.setAliasName(importPositionEntity.getAliasName());
    newImportPositionEntity.setPartitionId(importPositionEntity.getPartitionId());
    newImportPositionEntity.setIndexName(indexName);
    newImportPositionEntity.setPosition(newPosition);
    return newImportPositionEntity;
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

    if (partitionId != that.partitionId)
      return false;
    if (position != that.position)
      return false;
    if (aliasName != null ? !aliasName.equals(that.aliasName) : that.aliasName != null)
      return false;
    return indexName != null ? indexName.equals(that.indexName) : that.indexName == null;

  }

  @Override
  public int hashCode() {
	int result = super.hashCode();
    result = 31 * result + (aliasName != null ? aliasName.hashCode() : 0);
    result = 31 * result + partitionId;
    result = 31 * result + (int) (position ^ (position >>> 32));
    result = 31 * result + (indexName != null ? indexName.hashCode() : 0);
    return result;
  }

  @Override
  public String toString() {
    return "ImportPositionEntity{" + "aliasName='" + aliasName + '\'' + ", partitionId=" + partitionId + ", position=" + position + ", indexName='" + indexName
        + '\'' + '}';
  }
}
