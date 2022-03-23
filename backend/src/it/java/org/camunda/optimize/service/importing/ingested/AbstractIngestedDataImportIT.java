/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.importing.ingested;

import org.camunda.optimize.service.importing.AbstractImportIT;
import org.junit.jupiter.api.BeforeEach;

public class AbstractIngestedDataImportIT extends AbstractImportIT {
  @BeforeEach
  public void enableExternalVariableImport() {
    embeddedOptimizeExtension.getConfigurationService()
      .getExternalVariableConfiguration()
      .getImportConfiguration()
      .setEnabled(true);
  }

  protected void importIngestedDataFromScratchRefreshIndicesBeforeAndAfter() {
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();
    embeddedOptimizeExtension.importIngestedDataFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();
  }

  protected void importIngestedDataFromLastIndexRefreshIndicesBeforeAndAfter() {
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();
    embeddedOptimizeExtension.importIngestedDataFromLastIndex();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();
  }
}
