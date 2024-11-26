/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.test.upgrade;

import static org.assertj.core.api.Assertions.assertThat;

import co.elastic.clients.elasticsearch.indices.TemplateMapping;
import co.elastic.clients.elasticsearch.indices.get_alias.IndexAliases;
import co.elastic.clients.elasticsearch.indices.get_mapping.IndexMappingRecord;
import io.camunda.optimize.service.util.configuration.DatabaseType;
import io.camunda.optimize.test.upgrade.client.ElasticsearchSchemaTestClient;
import java.io.IOException;
import java.util.Map;
import java.util.Map.Entry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class UpgradeElasticsearchSchemaIT
    extends AbstractDatabaseSchemaIT<ElasticsearchSchemaTestClient> {
  private static final Logger LOG = LoggerFactory.getLogger(UpgradeElasticsearchSchemaIT.class);

  Map<String, Map> expectedSettings;
  Map<String, IndexMappingRecord> expectedMappings;
  Map<String, IndexAliases> expectedAliases;
  Map<String, TemplateMapping> expectedTemplates;

  @Override
  protected String getOptimizeUpdateLogPath() {
    return getBuildDirectory() + "/es-update-schema-optimize-upgrade.log";
  }

  @Override
  protected String getNewOptimizeOutputLogPath() {
    return getBuildDirectory() + "/es-update-schema-new-optimize-startup.log";
  }

  @Override
  protected String getOldOptimizeOutputLogPath() {
    return getBuildDirectory() + "/es-update-schema-old-optimize-startup.log";
  }

  @Override
  protected void assertMigratedDatabaseIndicesMatchExpected() throws IOException {
    LOG.info(
        "Expected settings size: {}, keys: {}", expectedSettings.size(), expectedSettings.keySet());
    final Map<String, Map> newSettings = newDatabaseSchemaClient.getSettings();
    LOG.info("Actual settings size: {}, keys: {}", newSettings.size(), newSettings.keySet());
    assertThat(newSettings).isEqualTo(expectedSettings);
    assertThat(newDatabaseSchemaClient.getMappings())
        .isEqualToComparingFieldByFieldRecursively(expectedMappings);
  }

  @Override
  protected void assertMigratedDatabaseAliasesMatchExpected() throws IOException {
    LOG.info(
        "Expected aliases size: {}, keys: {}", expectedAliases.size(), expectedAliases.keySet());
    final Map<String, IndexAliases> newAliases = newDatabaseSchemaClient.getAliases();
    LOG.info("Actual aliases size: {}, keys: {}", newAliases.size(), newAliases.keySet());
    assertThat(newAliases).isEqualToComparingFieldByFieldRecursively(expectedAliases);
  }

  @Override
  protected void assertMigratedDatabaseTemplatesMatchExpected() throws IOException {
    LOG.info(
        "Expected templates size: {}, names: {}",
        expectedTemplates.size(),
        expectedTemplates.keySet());
    final Map<String, TemplateMapping> newTemplates = newDatabaseSchemaClient.getTemplates();
    LOG.info("Actual templates size: {}, names: {}", newTemplates.size(), newTemplates.keySet());
    for (Entry<String, TemplateMapping> stringTemplateMappingEntry :
        newDatabaseSchemaClient.getTemplates().entrySet()) {
      assertThat(expectedTemplates.get(stringTemplateMappingEntry.getKey()).toString())
          .isEqualTo(stringTemplateMappingEntry.getValue().toString());
    }
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
    oldDatabaseSchemaClient = new ElasticsearchSchemaTestClient("old", getOldDatabasePort());
    oldDatabaseSchemaClient.cleanIndicesAndTemplates();
    newDatabaseSchemaClient = new ElasticsearchSchemaTestClient("new", getNewDatabasePort());
    newDatabaseSchemaClient.cleanIndicesAndTemplates();
  }

  @Override
  protected DatabaseType getDatabaseType() {
    return DatabaseType.ELASTICSEARCH;
  }
}
