/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.upgrade.steps.schema;

import static io.camunda.optimize.upgrade.steps.UpgradeStepType.SCHEMA_DELETE_TEMPLATE;

import io.camunda.optimize.service.db.schema.IndexMappingCreator;
import io.camunda.optimize.service.db.schema.OptimizeIndexNameService;
import io.camunda.optimize.upgrade.es.SchemaUpgradeClient;
import io.camunda.optimize.upgrade.exception.UpgradeRuntimeException;
import io.camunda.optimize.upgrade.steps.UpgradeStep;
import io.camunda.optimize.upgrade.steps.UpgradeStepType;
import lombok.EqualsAndHashCode;
import lombok.Getter;

@EqualsAndHashCode(callSuper = true)
public class DeleteIndexTemplateIfExistsStep extends UpgradeStep {

  // This should be the name of the template prefix and without version suffix or optimize prefix
  @Getter private final String templateName;
  @Getter private final int templateVersion;

  public DeleteIndexTemplateIfExistsStep(final String templateName, final int templateVersion) {
    super(null);
    this.templateName = templateName;
    this.templateVersion = templateVersion;
  }

  @Override
  public UpgradeStepType getType() {
    return SCHEMA_DELETE_TEMPLATE;
  }

  @Override
  public IndexMappingCreator getIndex() {
    throw new UpgradeRuntimeException(
        "Index class does not exist as its template is being deleted");
  }

  @Override
  public void execute(final SchemaUpgradeClient schemaUpgradeClient) {
    final String indexAlias =
        schemaUpgradeClient.getIndexNameService().getOptimizeIndexAliasForIndex(templateName);
    schemaUpgradeClient.getAliasMap(indexAlias).keySet().stream()
        .filter(templateName -> templateName.contains(this.templateName))
        .forEach(schemaUpgradeClient::deleteTemplateIfExists);
  }

  public String getVersionedTemplateNameWithTemplateSuffix() {
    return String.format(
        "%s[Template]",
        OptimizeIndexNameService.getOptimizeIndexOrTemplateNameForAliasAndVersion(
            templateName, String.valueOf(templateVersion)));
  }
}
