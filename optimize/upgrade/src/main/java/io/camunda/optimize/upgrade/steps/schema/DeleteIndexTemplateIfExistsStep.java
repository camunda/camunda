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

  public boolean equals(final Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof DeleteIndexTemplateIfExistsStep)) {
      return false;
    }
    final DeleteIndexTemplateIfExistsStep other = (DeleteIndexTemplateIfExistsStep) o;
    if (!other.canEqual((Object) this)) {
      return false;
    }
    if (!super.equals(o)) {
      return false;
    }
    final Object this$templateName = this.templateName;
    final Object other$templateName = other.templateName;
    if (this$templateName == null
        ? other$templateName != null
        : !this$templateName.equals(other$templateName)) {
      return false;
    }
    if (this.templateVersion != other.templateVersion) {
      return false;
    }
    return true;
  }

  protected boolean canEqual(final Object other) {
    return other instanceof DeleteIndexTemplateIfExistsStep;
  }

  public int hashCode() {
    final int PRIME = 59;
    int result = super.hashCode();
    final Object $templateName = this.templateName;
    result = result * PRIME + ($templateName == null ? 43 : $templateName.hashCode());
    result = result * PRIME + this.templateVersion;
    return result;
  }

  public String getTemplateName() {
    return this.templateName;
  }

  public int getTemplateVersion() {
    return this.templateVersion;
  }
}
