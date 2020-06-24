/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.upgrade.version30;

import lombok.SneakyThrows;
import org.camunda.optimize.dto.optimize.importing.index.AllEntitiesBasedImportIndexDto;
import org.camunda.optimize.upgrade.main.impl.UpgradeFrom30To31;
import org.camunda.optimize.upgrade.plan.UpgradePlan;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.IMPORT_INDEX_INDEX_NAME;

public class RemoveDefinitionImportIndexDocumentsIT extends AbstractUpgrade30IT {

  @BeforeEach
  @Override
  public void setUp() throws Exception {
    super.setUp();

    executeBulk("steps/3.0/report_data/30-import-index-bulk");
  }

  @SneakyThrows
  @Test
  public void reportFiltersAreMigrated() {
    // given
    final UpgradePlan upgradePlan = new UpgradeFrom30To31().buildUpgradePlan();

    // when
    upgradePlan.execute();

    // then
    final List<AllEntitiesBasedImportIndexDto> importIndexDocuments = getAllDocumentsOfIndexAs(
      IMPORT_INDEX_INDEX_NAME,
      AllEntitiesBasedImportIndexDto.class
    );
    assertThat(importIndexDocuments)
      .hasSize(1);
  }

}
