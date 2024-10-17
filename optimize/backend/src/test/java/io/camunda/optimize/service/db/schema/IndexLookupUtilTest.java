/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.schema;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.optimize.service.db.es.schema.ElasticSearchSchemaManager;
import io.camunda.optimize.service.db.es.schema.index.DecisionInstanceIndexES;
import io.camunda.optimize.service.db.es.schema.index.ProcessInstanceIndexES;
import io.camunda.optimize.service.db.os.schema.OpenSearchSchemaManager;
import io.camunda.optimize.service.db.os.schema.index.DecisionInstanceIndexOS;
import io.camunda.optimize.service.db.os.schema.index.ProcessInstanceIndexOS;
import io.camunda.optimize.service.util.configuration.DatabaseType;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

public class IndexLookupUtilTest {

  @Test
  public void allNonDynamicIndicesHaveMappingsFromElasticSearchToOpenSearch() {
    // given
    final List<String> knownOpensearchNonDynamicIndexName =
        OpenSearchSchemaManager.getAllNonDynamicMappings().stream()
            .map(IndexMappingCreator::getIndexName)
            .toList();

    // when
    final List<String> convertedIndexNames =
        ElasticSearchSchemaManager.getAllNonDynamicMappings().stream()
            .map(
                esIndex ->
                    IndexLookupUtil.convertIndexForDatabase(esIndex, DatabaseType.OPENSEARCH))
            .map(IndexMappingCreator::getIndexName)
            .toList();

    // then
    assertThat(convertedIndexNames)
        .allSatisfy(
            convertedIndex -> assertThat(convertedIndex).isIn(knownOpensearchNonDynamicIndexName));
  }

  @Test
  public void allDynamicIndicesHaveMappingsFromElasticSearchToOpenSearch() {
    // given
    final String defKey = "key";
    final List<IndexMappingCreator> dynamicESIndices =
        List.of(new DecisionInstanceIndexES(defKey), new ProcessInstanceIndexES(defKey));

    // when
    final List<String> convertedIndexClassNames =
        dynamicESIndices.stream()
            .map(
                esIndex ->
                    IndexLookupUtil.convertIndexForDatabase(esIndex, DatabaseType.OPENSEARCH))
            .map(index -> index.getClass().getSimpleName())
            .toList();

    // then
    assertThat(convertedIndexClassNames)
        .containsExactlyInAnyOrderElementsOf(
            Stream.of(new DecisionInstanceIndexOS(defKey), new ProcessInstanceIndexOS(defKey))
                .map(index -> index.getClass().getSimpleName())
                .toList());
  }

  @Test
  public void allNonDynamicIndicesHaveMappingsFromOpenSearchToElasticSearch() {
    // given
    final List<String> knownOpensearchNonDynamicIndexNames =
        OpenSearchSchemaManager.getAllNonDynamicMappings().stream()
            .map(IndexMappingCreator::getIndexName)
            .toList();

    // when
    final List<String> convertedIndexNames =
        ElasticSearchSchemaManager.getAllNonDynamicMappings().stream()
            .map(
                esIndex ->
                    IndexLookupUtil.convertIndexForDatabase(esIndex, DatabaseType.ELASTICSEARCH))
            .map(IndexMappingCreator::getIndexName)
            .toList();

    // then
    assertThat(convertedIndexNames)
        .allSatisfy(
            convertedIndex -> assertThat(convertedIndex).isIn(knownOpensearchNonDynamicIndexNames));
  }

  @Test
  public void allDynamicIndicesHaveMappingsFromOpenSearchToElasticSearch() {
    // given
    final String defKey = "key";
    final List<IndexMappingCreator> dynamicESIndices =
        List.of(new DecisionInstanceIndexOS(defKey), new ProcessInstanceIndexOS(defKey));

    // when
    final List<String> convertedIndexClassNames =
        dynamicESIndices.stream()
            .map(
                esIndex ->
                    IndexLookupUtil.convertIndexForDatabase(esIndex, DatabaseType.ELASTICSEARCH))
            .map(index -> index.getClass().getSimpleName())
            .toList();

    // then
    assertThat(convertedIndexClassNames)
        .containsExactlyInAnyOrderElementsOf(
            Stream.of(new DecisionInstanceIndexES(defKey), new ProcessInstanceIndexES(defKey))
                .map(index -> index.getClass().getSimpleName())
                .toList());
  }
}
