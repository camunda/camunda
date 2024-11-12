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
import io.camunda.optimize.upgrade.db.SchemaUpgradeClient;
import io.camunda.optimize.upgrade.exception.UpgradeRuntimeException;
import io.camunda.optimize.upgrade.steps.UpgradeStep;
import io.camunda.optimize.upgrade.steps.UpgradeStepType;

public class DeleteIndexTemplateIfExistsStep extends UpgradeStep {

  // This should be the name of the template prefix and without version suffix or optimize prefix
  private final String templateName;
  private final int templateVersion;

  public DeleteIndexTemplateIfExistsStep(final String templateName, final int templateVersion) {
    super(null);
    this.templateName = templateName;
    this.templateVersion = templateVersion;
  }

  @Override
  public IndexMappingCreator getIndex() {
    throw new UpgradeRuntimeException(
        "Index class does not exist as its template is being deleted");
  }

  @Override
  public UpgradeStepType getType() {
    return SCHEMA_DELETE_TEMPLATE;
  }

  @Override
  public void performUpgradeStep(final SchemaUpgradeClient schemaUpgradeClient) {
    final String fullTemplateName =
        schemaUpgradeClient
            .getIndexNameService()
            .getOptimizeIndexOrTemplateNameForAliasAndVersionWithPrefix(
                templateName, String.valueOf(templateVersion));
    schemaUpgradeClient.deleteTemplateIfExists(fullTemplateName);
  }

  public String getVersionedTemplateNameWithTemplateSuffix() {
    return String.format(
        "%s[Template]",
        OptimizeIndexNameService.getOptimizeIndexOrTemplateNameForAliasAndVersion(
            templateName, String.valueOf(templateVersion)));
  }

  @Override
  public boolean equals(final Object o) {
    return org.apache.commons.lang3.builder.EqualsBuilder.reflectionEquals(this, o);
  }

  @Override
  protected boolean canEqual(final Object other) {
    return other instanceof DeleteIndexTemplateIfExistsStep;
  }

  @Override
  public int hashCode() {
    return org.apache.commons.lang3.builder.HashCodeBuilder.reflectionHashCode(this);
  }

  public String getTemplateName() {
    return this.templateName;
  }

  public int getTemplateVersion() {
    return this.templateVersion;
  }
}
