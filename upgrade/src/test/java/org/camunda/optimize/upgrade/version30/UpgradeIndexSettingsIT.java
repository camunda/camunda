/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.upgrade.version30;

import lombok.SneakyThrows;
import org.camunda.optimize.service.es.schema.IndexMappingCreator;
import org.camunda.optimize.service.es.schema.index.index.ImportIndexIndex;
import org.camunda.optimize.upgrade.main.impl.UpgradeFrom30To31;
import org.camunda.optimize.upgrade.plan.UpgradePlan;
import org.elasticsearch.action.admin.indices.settings.get.GetSettingsRequest;
import org.elasticsearch.action.admin.indices.settings.get.GetSettingsResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;
import static org.camunda.optimize.service.es.schema.IndexSettingsBuilder.buildAnalysisSettings;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.ANALYSIS_SETTING;

public class UpgradeIndexSettingsIT extends AbstractUpgrade30IT {

  @BeforeEach
  protected void setUp() throws Exception {
    super.setUp();
  }

  @SneakyThrows
  @Test
  public void upgradedIndicesHaveCorrectTokenizerType() {
    // given
    final UpgradePlan upgradePlan = new UpgradeFrom30To31().buildUpgradePlan();

    // when
    upgradePlan.execute();

    // then
    final Settings settingsAfterUpgrade = getIndexSettings(new ImportIndexIndex());
    final Settings expectedSettings = buildAnalysisSettings();

    assertThat(settingsAfterUpgrade.get("analysis.tokenizer.ngram_tokenizer.type"))
      .isEqualTo(expectedSettings.get("index.analysis.tokenizer.ngram_tokenizer.type"));
  }

  private Settings getIndexSettings(final IndexMappingCreator index) throws IOException {
    final String indexName = indexNameService.getVersionedOptimizeIndexNameForIndexMapping(index);
    final GetSettingsRequest getSettingsRequest = new GetSettingsRequest();
    getSettingsRequest.indices(indexName);
    final GetSettingsResponse response =
      prefixAwareClient.getHighLevelClient().indices().getSettings(getSettingsRequest, RequestOptions.DEFAULT);
    return response.getIndexToSettings().get(indexName);
  }

  private XContentBuilder addAnalysis(XContentBuilder builder) throws IOException {
    // @formatter:off
    return builder
      .startObject(ANALYSIS_SETTING)
        .startObject("analyzer")
          .startObject("lowercase_nGram")
            .field("type", "custom")
            .field("tokenizer", "nGram_tokenizer")
            .field("filter", "lowercase")
          .endObject()
          .startObject("is_present_analyzer")
            .field("type", "custom")
            .field("tokenizer", "keyword")
            .field("filter", "is_present_filter")
          .endObject()
        .endObject()
        .startObject("normalizer")
          .startObject("lowercase_normalizer")
            .field("type", "custom")
            .field("filter", new String[]{"lowercase"})
          .endObject()
        .endObject()
        .startObject("tokenizer")
          .startObject("nGram_tokenizer")
            .field("type", "nGram")
            .field("min_gram", 1)
            .field("max_gram", 10)
          .endObject()
        .endObject()
        .startObject("filter")
          .startObject("is_present_filter")
            .field("type", "truncate")
            .field("length", "1")
          .endObject()
        .endObject()
      .endObject();
    // @formatter:on
  }
}
