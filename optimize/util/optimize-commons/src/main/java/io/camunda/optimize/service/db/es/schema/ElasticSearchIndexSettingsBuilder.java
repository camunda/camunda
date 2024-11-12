/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.es.schema;

import static io.camunda.optimize.service.db.DatabaseConstants.IS_PRESENT_ANALYZER;
import static io.camunda.optimize.service.db.DatabaseConstants.IS_PRESENT_FILTER;
import static io.camunda.optimize.service.db.DatabaseConstants.LOWERCASE_NGRAM;
import static io.camunda.optimize.service.db.DatabaseConstants.LOWERCASE_NORMALIZER;
import static io.camunda.optimize.service.db.DatabaseConstants.MAX_GRAM;
import static io.camunda.optimize.service.db.DatabaseConstants.NGRAM_TOKENIZER;

import co.elastic.clients.elasticsearch._types.analysis.Analyzer;
import co.elastic.clients.elasticsearch._types.analysis.Normalizer;
import co.elastic.clients.elasticsearch._types.analysis.TokenChar;
import co.elastic.clients.elasticsearch._types.analysis.TokenFilter;
import co.elastic.clients.elasticsearch._types.analysis.Tokenizer;
import co.elastic.clients.elasticsearch.indices.IndexSettings;
import io.camunda.optimize.service.db.schema.IndexMappingCreator;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import java.io.IOException;
import java.util.List;

public class ElasticSearchIndexSettingsBuilder {

  public static IndexSettings buildDynamicSettings(
      final ConfigurationService configurationService) {
    return IndexSettings.of(
        i ->
            i.maxNgramDiff(MAX_GRAM - 1)
                .refreshInterval(
                    t ->
                        t.time(
                            configurationService
                                .getElasticSearchConfiguration()
                                .getRefreshInterval()))
                .numberOfReplicas(
                    configurationService
                        .getElasticSearchConfiguration()
                        .getNumberOfReplicas()
                        .toString())
                .mapping(
                    m ->
                        m.nestedObjects(
                            n ->
                                n.limit(
                                    configurationService
                                        .getElasticSearchConfiguration()
                                        .getNestedDocumentsLimit()))));
  }

  public static IndexSettings buildAllSettings(
      final ConfigurationService configurationService,
      final IndexMappingCreator<IndexSettings.Builder> indexMappingCreator)
      throws IOException {
    return IndexSettings.of(
        i -> {
          i.maxNgramDiff(MAX_GRAM - 1)
              .refreshInterval(
                  t ->
                      t.time(
                          configurationService
                              .getElasticSearchConfiguration()
                              .getRefreshInterval()))
              .numberOfReplicas(
                  configurationService
                      .getElasticSearchConfiguration()
                      .getNumberOfReplicas()
                      .toString())
              .mapping(
                  m ->
                      m.nestedObjects(
                          n ->
                              n.limit(
                                  configurationService
                                      .getElasticSearchConfiguration()
                                      .getNestedDocumentsLimit())));
          try {
            indexMappingCreator.getStaticSettings(i, configurationService);
          } catch (final IOException e) {
            throw new RuntimeException(e);
          }
          // this analyzer is supposed to be used for large text fields for which we only want to
          // query for whether they are empty or not, e.g. the xml of definitions
          // see https://app.camunda.com/jira/browse/OPT-2911
          i.analysis(
              a ->
                  a.analyzer(
                          LOWERCASE_NGRAM,
                          Analyzer.of(
                              y -> y.custom(c -> c.tokenizer(NGRAM_TOKENIZER).filter("lowercase"))))
                      .analyzer(
                          IS_PRESENT_ANALYZER,
                          Analyzer.of(
                              y -> y.custom(c -> c.tokenizer("keyword").filter(IS_PRESENT_FILTER))))
                      .normalizer(
                          LOWERCASE_NORMALIZER,
                          Normalizer.of(n -> n.custom(c -> c.filter("lowercase"))))
                      .tokenizer(
                          NGRAM_TOKENIZER,
                          Tokenizer.of(
                              t ->
                                  t.definition(
                                      d ->
                                          d.ngram(
                                              n ->
                                                  n.minGram(1)
                                                      .tokenChars(
                                                          List.of(
                                                              TokenChar.Letter,
                                                              TokenChar.Digit,
                                                              TokenChar.Whitespace,
                                                              TokenChar.Punctuation,
                                                              TokenChar.Symbol))
                                                      .maxGram(MAX_GRAM)))))
                      .filter(
                          IS_PRESENT_FILTER,
                          TokenFilter.of(t -> t.definition(d -> d.truncate(f -> f.length(1))))));
          return i;
        });
  }
}
