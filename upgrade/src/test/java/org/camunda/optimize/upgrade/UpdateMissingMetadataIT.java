/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.upgrade;

import io.github.netmikey.logunit.api.LogCapturer;
import org.camunda.optimize.service.es.schema.ElasticsearchMetadataService;
import org.camunda.optimize.service.es.schema.index.MetadataIndex;
import org.camunda.optimize.upgrade.main.UpgradeProcedure;
import org.camunda.optimize.upgrade.plan.UpgradePlan;
import org.camunda.optimize.upgrade.plan.factories.CurrentVersionNoOperationUpgradePlanFactory;
import org.elasticsearch.ElasticsearchStatusException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.mockserver.model.HttpResponse;
import org.mockserver.model.HttpStatusCode;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.METADATA_INDEX_NAME;
import static org.mockserver.model.HttpRequest.request;

public class UpdateMissingMetadataIT extends AbstractUpgradeIT {
  @RegisterExtension
  protected final LogCapturer logCapturer =
    LogCapturer.create().captureForType(UpgradeProcedure.class).captureForType(ElasticsearchMetadataService.class);

  @Test
  public void updateIsSkippedIfNoMetadataIndexExists() {
    // given
    cleanAllDataFromElasticsearch();
    final UpgradePlan upgradePlan = new CurrentVersionNoOperationUpgradePlanFactory().createUpgradePlan();

    // when
    upgradeProcedure.performUpgrade(upgradePlan);

    // then it is skipped
    logCapturer.assertContains("Optimize Metadata index wasn't found, thus no metadata available.");
    logCapturer.assertContains(
      "No Connection to elasticsearch or no Optimize Metadata index found, skipping update to "
        + upgradePlan.getToVersion().getValue() + "."
    );
  }

  @Test
  public void updateIsSkippedIfNoMetadataDocExists() {
    // given
    deleteAllDocsInIndex(new MetadataIndex());
    final UpgradePlan upgradePlan = new CurrentVersionNoOperationUpgradePlanFactory().createUpgradePlan();

    // when
    upgradeProcedure.performUpgrade(upgradePlan);

    // then it is skipped
    logCapturer.assertContains(
      "Optimize Metadata index exists but no metadata doc was found, thus no metadata available.");
    logCapturer.assertContains(
      "No Connection to elasticsearch or no Optimize Metadata index found, skipping update to "
        + upgradePlan.getToVersion().getValue() + "."
    );
  }

  @Test
  public void updateFailsIfMetadataDocReadFails() {
    // given
    final UpgradePlan upgradePlan = new CurrentVersionNoOperationUpgradePlanFactory().createUpgradePlan();
    esMockServer
      .when(request().withPath("/.*-" + METADATA_INDEX_NAME + ".*/_doc/" + MetadataIndex.ID))
      .respond(HttpResponse.response().withStatusCode(HttpStatusCode.INTERNAL_SERVER_ERROR_500.code()));

    // when
    assertThatThrownBy(() -> upgradeProcedure.performUpgrade(upgradePlan))
      .hasMessageContaining("Failed retrieving the Optimize metadata document from elasticsearch!")
      .hasCauseInstanceOf(ElasticsearchStatusException.class);

    logCapturer.assertContains("Failed retrieving the Optimize metadata document from elasticsearch!");
  }
}
