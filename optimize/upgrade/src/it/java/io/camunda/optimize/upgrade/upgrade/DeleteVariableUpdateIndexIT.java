/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.upgrade.upgrade;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import io.camunda.optimize.service.db.schema.OptimizeIndexNameService;
import java.io.IOException;
import org.junit.jupiter.api.Test;

public class DeleteVariableUpdateIndexIT extends AbstractUpgrade88IT {

  @Test
  public void variableUpdateInstanceIndexIsDelete() throws IOException {
    // given variable update instance index exists
    assertThat(databaseIntegrationTestExtension.getIndexNames())
        .contains(expectedVariableUpdateIndexName());

    // when
    performUpgrade();

    // then the upgrade has deleted the index
    assertThat(databaseIntegrationTestExtension.getIndexNames())
        .doesNotContain(expectedVariableUpdateIndexName());
  }

  private String expectedVariableUpdateIndexName() {
    return OptimizeIndexNameService.getOptimizeIndexOrTemplateNameForAliasAndVersion(
            OptimizeIndexNameService.getOptimizeIndexAliasForIndexNameAndPrefix(
                variableUpdateInstanceIndexOld.getIndexName(), getIndexPrefix()),
            String.valueOf(variableUpdateInstanceIndexOld.getVersion()))
        + variableUpdateInstanceIndexOld.getIndexNameInitialSuffix();
  }

  private String getIndexPrefix() {
    if (!isElasticSearchUpgrade()) {
      return configurationService.getOpenSearchConfiguration().getIndexPrefix();
    } else {
      return configurationService.getElasticSearchConfiguration().getIndexPrefix();
    }
  }
}
