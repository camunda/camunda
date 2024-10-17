/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.upgrade.steps.schema;

import io.camunda.optimize.service.db.schema.OptimizeIndexNameService;
import io.camunda.optimize.upgrade.db.SchemaUpgradeClient;
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
    skipIndexConversion = true;
  }

  @Override
  public void performUpgradeStep(final SchemaUpgradeClient<?, ?> schemaUpgradeClient) {
    final OptimizeIndexNameService indexNameService = schemaUpgradeClient.getIndexNameService();
    final String indexAlias = indexNameService.getOptimizeIndexAliasForIndex(indexName);
    schemaUpgradeClient.getAliases(indexAlias).stream()
        .filter(indexName -> indexName.contains(this.indexName))
        .forEach(schemaUpgradeClient::deleteIndexIfExists);
  }

  public String getVersionedIndexName() {
    return OptimizeIndexNameService.getOptimizeIndexOrTemplateNameForAliasAndVersion(
        indexName, String.valueOf(indexVersion));
  }

  @Override
  public UpgradeStepType getType() {
    return UpgradeStepType.SCHEMA_DELETE_INDEX;
  }

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
    final Object this$indexName = this.indexName;
    final Object other$indexName = other.indexName;
    if (this$indexName == null
        ? other$indexName != null
        : !this$indexName.equals(other$indexName)) {
      return false;
    }
    if (this.indexVersion != other.indexVersion) {
      return false;
    }
    return true;
  }

  protected boolean canEqual(final Object other) {
    return other instanceof DeleteIndexIfExistsStep;
  }

  public int hashCode() {
    final int PRIME = 59;
    int result = super.hashCode();
    final Object $indexName = this.indexName;
    result = result * PRIME + ($indexName == null ? 43 : $indexName.hashCode());
    result = result * PRIME + this.indexVersion;
    return result;
  }

  public String getIndexName() {
    return this.indexName;
  }

  public int getIndexVersion() {
    return this.indexVersion;
  }
}
