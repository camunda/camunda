/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.upgrade;

import static io.camunda.optimize.service.db.DatabaseConstants.METADATA_INDEX_NAME;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockserver.model.HttpRequest.request;

import io.camunda.optimize.service.db.es.schema.ElasticSearchMetadataService;
import io.camunda.optimize.service.db.es.schema.index.MetadataIndexES;
import io.camunda.optimize.service.db.os.schema.OpenSearchMetadataService;
import io.camunda.optimize.service.db.schema.index.MetadataIndex;
import io.camunda.optimize.upgrade.main.UpgradeProcedure;
import io.camunda.optimize.upgrade.plan.UpgradePlan;
import io.camunda.optimize.upgrade.plan.factories.CurrentVersionNoOperationUpgradePlanFactory;
import io.github.netmikey.logunit.api.LogCapturer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.mockserver.model.HttpResponse;
import org.mockserver.model.HttpStatusCode;

public class UpdateMissingMetadataIT extends AbstractUpgradeIT {
  @RegisterExtension
  protected final LogCapturer logCapturer =
      LogCapturer.create()
          .captureForType(UpgradeProcedure.class)
          .captureForType(ElasticSearchMetadataService.class)
          .captureForType(OpenSearchMetadataService.class);

  @Test
  public void updateIsSkippedIfNoMetadataIndexExists() {
    // given
    cleanAllDataFromDatabase();
    final UpgradePlan upgradePlan =
        new CurrentVersionNoOperationUpgradePlanFactory().createUpgradePlan();

    // when
    upgradeProcedure.performUpgrade(upgradePlan);

    // then it is skipped
    logCapturer.assertContains("Optimize Metadata index wasn't found, thus no metadata available.");
    logCapturer.assertContains(
        "No Connection to database or no Optimize Metadata index found, skipping update to "
            + upgradePlan.getToVersion().getValue()
            + ".");
  }

  @Test
  public void updateIsSkippedIfNoMetadataDocExists() {
    // given
    deleteAllDocsInIndex(new MetadataIndexES());
    final UpgradePlan upgradePlan =
        new CurrentVersionNoOperationUpgradePlanFactory().createUpgradePlan();

    // when
    upgradeProcedure.performUpgrade(upgradePlan);

    // then it is skipped
    logCapturer.assertContains(
        "Optimize Metadata index exists but no metadata doc was found, thus no metadata available.");
    logCapturer.assertContains(
        "No Connection to database or no Optimize Metadata index found, skipping update to "
            + upgradePlan.getToVersion().getValue()
            + ".");
  }

  @Test
  public void updateFailsIfMetadataDocReadFails() {
    // given
    final UpgradePlan upgradePlan =
        new CurrentVersionNoOperationUpgradePlanFactory().createUpgradePlan();
    dbMockServer
        .when(request().withPath("/.*-" + METADATA_INDEX_NAME + ".*/_doc/" + MetadataIndex.ID))
        .respond(
            HttpResponse.response()
                .withStatusCode(HttpStatusCode.INTERNAL_SERVER_ERROR_500.code()));

    // when
    assertThatThrownBy(() -> upgradeProcedure.performUpgrade(upgradePlan))
        .hasMessageContaining("Failed retrieving the Optimize metadata document from database!");

    logCapturer.assertContains("Failed retrieving the Optimize metadata document from database!");
  }
}
