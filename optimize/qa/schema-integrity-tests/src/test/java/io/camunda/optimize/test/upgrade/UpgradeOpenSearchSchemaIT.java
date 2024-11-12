/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.test.upgrade;

import io.camunda.optimize.service.util.configuration.DatabaseType;
import io.camunda.optimize.test.upgrade.client.OpenSearchSchemaTestClient;
import java.io.IOException;
import java.util.Map;
import org.opensearch.client.opensearch.indices.IndexState;
import org.opensearch.client.opensearch.indices.TemplateMapping;
import org.opensearch.client.opensearch.indices.get_alias.IndexAliases;
import org.opensearch.client.opensearch.indices.get_mapping.IndexMappingRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class UpgradeOpenSearchSchemaIT
    extends AbstractDatabaseSchemaIT<OpenSearchSchemaTestClient> {
  private static final Logger LOG = LoggerFactory.getLogger(UpgradeOpenSearchSchemaIT.class);

  Map<String, IndexState> expectedSettings;
  Map<String, IndexMappingRecord> expectedMappings;
  Map<String, IndexAliases> expectedAliases;
  Map<String, TemplateMapping> expectedTemplates;

  @Override
  protected String getOptimizeUpdateLogPath() {
    return getBuildDirectory() + "/os-update-schema-optimize-upgrade.log";
  }

  @Override
  protected String getNewOptimizeOutputLogPath() {
    return getBuildDirectory() + "/os-update-schema-new-optimize-startup.log";
  }

  @Override
  protected String getOldOptimizeOutputLogPath() {
    return getBuildDirectory() + "/os-update-schema-old-optimize-startup.log";
  }

  @Override
  protected void assertMigratedDatabaseIndicesMatchExpected() throws IOException {
    LOG.info(
        "Expected settings size: {}, keys: {}", expectedSettings.size(), expectedSettings.keySet());
    final Map<String, IndexState> actualSettings = newDatabaseSchemaClient.getSettings();
    LOG.info("Actual settings size: {}, keys: {}", actualSettings.size(), actualSettings.keySet());

    assertMapContentEqualityFieldByField(expectedSettings, actualSettings, TOKEN_CHARS_FIELD_PATH);
    assertMapContentEqualityFieldByField(
        expectedMappings, newDatabaseSchemaClient.getMappings(), null);
  }

  @Override
  protected void assertMigratedDatabaseAliasesMatchExpected() throws IOException {
    LOG.info(
        "Expected aliases size: {}, keys: {}", expectedAliases.size(), expectedAliases.keySet());
    final Map<String, IndexAliases> actualAliases = newDatabaseSchemaClient.getAliases();
    LOG.info("Actual aliases size: {}, keys: {}", actualAliases.size(), actualAliases.keySet());
    assertMapContentEqualityFieldByField(expectedAliases, actualAliases, null);
  }

  @Override
  protected void assertMigratedDatabaseTemplatesMatchExpected() throws IOException {
    LOG.info(
        "Expected templates size: {}, names: {}",
        expectedTemplates.size(),
        expectedTemplates.keySet());
    final Map<String, TemplateMapping> actualTemplates = newDatabaseSchemaClient.getTemplates();
    LOG.info(
        "Actual templates size: {}, names: {}", actualTemplates.size(), actualTemplates.keySet());
    assertMapContentEqualityFieldByField(expectedTemplates, actualTemplates, null);
  }

  @Override
  protected void saveNewOptimizeDatabaseStatus() throws IOException {
    expectedSettings = newDatabaseSchemaClient.getSettings();
    expectedMappings = newDatabaseSchemaClient.getMappings();
    expectedAliases = newDatabaseSchemaClient.getAliases();
    expectedTemplates = newDatabaseSchemaClient.getTemplates();
  }

  @Override
  protected void initializeClientAndCleanDatabase() throws IOException {
    oldDatabaseSchemaClient = new OpenSearchSchemaTestClient("old", getOldDatabasePort());
    oldDatabaseSchemaClient.cleanIndicesAndTemplates();
    newDatabaseSchemaClient = new OpenSearchSchemaTestClient("new", getNewDatabasePort());
    newDatabaseSchemaClient.cleanIndicesAndTemplates();
  }

  @Override
  protected DatabaseType getDatabaseType() {
    return DatabaseType.OPENSEARCH;
  }
}
