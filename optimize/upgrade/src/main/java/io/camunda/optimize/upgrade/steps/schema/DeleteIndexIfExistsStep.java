/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.upgrade.steps.schema;

import io.camunda.optimize.service.db.schema.IndexMappingCreator;
import io.camunda.optimize.service.db.schema.OptimizeIndexNameService;
import io.camunda.optimize.upgrade.es.SchemaUpgradeClient;
import io.camunda.optimize.upgrade.exception.UpgradeRuntimeException;
import io.camunda.optimize.upgrade.steps.UpgradeStep;
import io.camunda.optimize.upgrade.steps.UpgradeStepType;

public class DeleteIndexIfExistsStep extends UpgradeStep {

  // This should be the name of the index without prefix and without version suffix
  private final String indexName;
  private final int indexVersion;

  public DeleteIndexIfExistsStep(final String indexName, final int indexVersion) {
    super(null);
    this.indexName = indexName;
    this.indexVersion = indexVersion;
  }

  public String getVersionedIndexName() {
    return OptimizeIndexNameService.getOptimizeIndexOrTemplateNameForAliasAndVersion(
        indexName, String.valueOf(indexVersion));
  }

  @Override
  public UpgradeStepType getType() {
    return UpgradeStepType.SCHEMA_DELETE_INDEX;
  }

  @Override
  public void execute(final SchemaUpgradeClient schemaUpgradeClient) {
    final OptimizeIndexNameService indexNameService = schemaUpgradeClient.getIndexNameService();
    final String indexAlias = indexNameService.getOptimizeIndexAliasForIndex(indexName);
    schemaUpgradeClient.getAliasMap(indexAlias).keySet().stream()
        .filter(indexName -> indexName.contains(this.indexName))
        .forEach(schemaUpgradeClient::deleteIndexIfExists);
  }

  @Override
  public IndexMappingCreator getIndex() {
    throw new UpgradeRuntimeException("Index class does not exist as it is being deleted");
  }

  @Override
  protected boolean canEqual(final Object other) {
    return other instanceof DeleteIndexIfExistsStep;
  }

  @Override
  public int hashCode() {
    final int PRIME = 59;
    int result = super.hashCode();
    final Object $indexName = indexName;
    result = result * PRIME + ($indexName == null ? 43 : $indexName.hashCode());
    result = result * PRIME + indexVersion;
    return result;
  }

  @Override
  public boolean equals(final Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof DeleteIndexIfExistsStep)) {
      return false;
    }
    final DeleteIndexIfExistsStep other = (DeleteIndexIfExistsStep) o;
    if (!other.canEqual((Object) this)) {
      return false;
    }
    if (!super.equals(o)) {
      return false;
    }
    final Object this$indexName = indexName;
    final Object other$indexName = other.indexName;
    if (this$indexName == null
        ? other$indexName != null
        : !this$indexName.equals(other$indexName)) {
      return false;
    }
    if (indexVersion != other.indexVersion) {
      return false;
    }
    return true;
  }

  public String getIndexName() {
    return indexName;
  }

  public int getIndexVersion() {
    return indexVersion;
  }
}
