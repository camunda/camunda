/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.os.schema;

import org.camunda.optimize.service.db.schema.IndexMappingCreator;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.camunda.optimize.service.util.configuration.condition.OpenSearchCondition;
import org.opensearch.client.opensearch._types.Time;
import org.opensearch.client.opensearch._types.analysis.Analyzer;
import org.opensearch.client.opensearch._types.analysis.CustomAnalyzer;
import org.opensearch.client.opensearch._types.analysis.LowercaseNormalizer;
import org.opensearch.client.opensearch._types.analysis.NGramTokenizer;
import org.opensearch.client.opensearch._types.analysis.Normalizer;
import org.opensearch.client.opensearch._types.analysis.TokenFilter;
import org.opensearch.client.opensearch._types.analysis.TokenFilterDefinition;
import org.opensearch.client.opensearch._types.analysis.Tokenizer;
import org.opensearch.client.opensearch._types.analysis.TruncateTokenFilter;
import org.opensearch.client.opensearch.indices.IndexSettings;
import org.opensearch.client.opensearch.indices.IndexSettingsAnalysis;
import org.opensearch.client.opensearch.indices.IndexSettingsMapping;
import org.opensearch.client.opensearch.indices.IndexSettingsMappingLimit;
import org.springframework.context.annotation.Conditional;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.camunda.optimize.service.db.DatabaseConstants.IS_PRESENT_ANALYZER;
import static org.camunda.optimize.service.db.DatabaseConstants.IS_PRESENT_FILTER;
import static org.camunda.optimize.service.db.DatabaseConstants.LOWERCASE_NGRAM;
import static org.camunda.optimize.service.db.DatabaseConstants.LOWERCASE_NORMALIZER;
import static org.camunda.optimize.service.db.DatabaseConstants.MAX_GRAM;
import static org.camunda.optimize.service.db.DatabaseConstants.NGRAM_TOKENIZER;


@Conditional(OpenSearchCondition.class)
public class OpenSearchIndexSettingsBuilder {

  public static IndexSettings buildAllSettings(ConfigurationService configurationService,
                                               IndexMappingCreator<IndexSettings.Builder> indexMappingCreator) throws IOException {
    IndexSettings.Builder builder = new IndexSettings.Builder();
    addDynamicSettings(configurationService, builder);
    addStaticSettings(indexMappingCreator, configurationService, builder);
    builder = addAnalysis(builder);
    return builder.build();
  }

  public static IndexSettings buildDynamicSettings(ConfigurationService configurationService) {
    IndexSettings.Builder builder = new IndexSettings.Builder();
    builder = addDynamicSettings(configurationService, builder);
    return builder.build();
  }

  private static void addStaticSettings(final IndexMappingCreator<IndexSettings.Builder> indexMappingCreator,
                                        final ConfigurationService configurationService,
                                        final IndexSettings.Builder builder) throws IOException {
    indexMappingCreator.getStaticSettings(builder, configurationService);
  }

  private static IndexSettings.Builder addDynamicSettings(final ConfigurationService configurationService,
                                                          final IndexSettings.Builder settingsBuilder) {
    return settingsBuilder
      .maxNgramDiff(MAX_GRAM - 1)
      .refreshInterval(new Time.Builder().time(configurationService.getOpenSearchConfiguration().getRefreshInterval()).build())
      .numberOfReplicas(String.valueOf(configurationService.getOpenSearchConfiguration().getNumberOfReplicas()))
      .mapping(new IndexSettingsMapping.Builder()
                 .nestedObjects(
                   new IndexSettingsMappingLimit.Builder()
                     .limit((long) configurationService.getOpenSearchConfiguration().getNestedDocumentsLimit())
                     .build())
                 .build());
  }

  private static IndexSettings.Builder addAnalysis(final IndexSettings.Builder settingsBuilder) {

    Map<String, Analyzer> analyzers = new HashMap<>();
    analyzers.put(LOWERCASE_NGRAM,
                  new Analyzer.Builder().custom(
                    new CustomAnalyzer.Builder()
                      .tokenizer(NGRAM_TOKENIZER)
                      .filter("lowercase").build()).build());

    analyzers.put(
      IS_PRESENT_ANALYZER,
      new Analyzer.Builder().custom(
                    new CustomAnalyzer.Builder()
                      .tokenizer("keyword")
                      .filter(IS_PRESENT_FILTER).build()).build());


    IndexSettingsAnalysis analysis = new IndexSettingsAnalysis.Builder()
      .analyzer(analyzers)
      .normalizer(LOWERCASE_NORMALIZER, new Normalizer.Builder().lowercase(new LowercaseNormalizer.Builder().build()).build())
      .tokenizer(NGRAM_TOKENIZER,
                 new Tokenizer.Builder()
                   .definition(new NGramTokenizer.Builder()
                                 .minGram(1)
                                 .maxGram(MAX_GRAM)
                                 .tokenChars(List.of())
                                 .build()
                                 ._toTokenizerDefinition())
                   .build())
      .filter(
        IS_PRESENT_FILTER,
        new TokenFilter.Builder().definition(
                new TokenFilterDefinition.Builder()
                  .truncate(new TruncateTokenFilter.Builder().length(1).build())
                  .build())
                .build())
     .build();
    return settingsBuilder.analysis(analysis);
  }
}
