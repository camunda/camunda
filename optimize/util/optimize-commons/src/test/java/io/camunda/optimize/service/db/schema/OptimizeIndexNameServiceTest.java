/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.schema;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;

import io.camunda.optimize.service.db.es.schema.index.index.PositionBasedImportIndexES;
import org.junit.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

public class OptimizeIndexNameServiceTest {

  @ParameterizedTest
  @ValueSource(strings = {"", "my-prefix", "dev-optimize1"})
  public void shouldReturnIndexWithPrefixAndComponent(final String indexPrefix) {
    final var nameService = new OptimizeIndexNameService(indexPrefix);

    assertThat(
            nameService.getOptimizeIndexOrTemplateNameForAliasAndVersionWithPrefix(
                "position-index", "8"))
        .isEqualTo(withHyphen(indexPrefix) + "optimize-position-index_v8");
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "my-prefix", "dev-optimize-1"})
  public void shouldReturnIndexNameWithComponent(final String indexPrefix) {
    final var nameService = new OptimizeIndexNameService(indexPrefix);
    assertThat(nameService.getIndexPrefix()).isEqualTo(withHyphen(indexPrefix));
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "my-prefix", "dev-optimize-1"})
  public void shouldReturnIndexNameFromIndexCreator(final String indexPrefix) {
    final var nameService = new OptimizeIndexNameService(indexPrefix);
    final var index = new PositionBasedImportIndexES();

    assertThat(nameService.getOptimizeIndexAliasForIndex(index))
        .isEqualTo(withHyphen(indexPrefix) + "optimize-position-based-import-index");

    assertThat(nameService.getOptimizeIndexNameWithVersion(index))
        .isEqualTo(withHyphen(indexPrefix) + "optimize-position-based-import-index_v3");
    assertThat(nameService.getOptimizeIndexNameWithVersionWithoutSuffix(index))
        .isEqualTo(withHyphen(indexPrefix) + "optimize-position-based-import-index_v3");
    assertThat(nameService.getOptimizeIndexNameWithVersionWithWildcardSuffix(index))
        .isEqualTo(withHyphen(indexPrefix) + "optimize-position-based-import-index_v3*");

    assertThatThrownBy(() -> nameService.getOptimizeIndexTemplateNameWithVersion(index))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  public void setIndexPrefixUpdatesAllFields() {
    final var nameService = new OptimizeIndexNameService("my-index");
    nameService.setIndexPrefix("my-new-prefix");
    assertThat(nameService.getIndexPrefix()).isEqualTo("my-new-prefix-optimize");
    assertThat(nameService.getOptimizeIndexOrTemplateNameForAliasAndVersionWithPrefix("index", "3"))
        .isEqualTo("my-new-prefix-optimize-index_v3");
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "my-custom-prefix"})
  public void shouldBeIdempotentInPrefixing(final String prefix) {
    final var nameService = new OptimizeIndexNameService(prefix);
    final var aliased = nameService.getOptimizeIndexAliasForIndex("process-instance-index");
    assertThat(nameService.getOptimizeIndexAliasForIndex(aliased)).isEqualTo(aliased);
  }

  public static String withHyphen(final String s) {
    return s.isEmpty() ? "" : s + "-";
  }
}
