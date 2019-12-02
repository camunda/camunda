/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.upgrade.from26To27;

import lombok.SneakyThrows;
import org.camunda.optimize.dto.optimize.query.alert.AlertDefinitionDto;
import org.camunda.optimize.service.es.schema.StrictIndexMappingCreator;
import org.camunda.optimize.upgrade.AbstractUpgradeIT;
import org.camunda.optimize.upgrade.main.impl.UpgradeFrom26To27;
import org.camunda.optimize.upgrade.plan.UpgradePlan;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;

public class UpgradeRemoveOrphanedAlertsIT extends AbstractUpgradeIT {

  private final String expectedAlertId1 = "08f9f96e-15ab-41d7-b1b1-37fc46f568a1";
  private final String expectedAlertId2 = "08f9f96e-15ab-41d7-b1b1-37fc46f568a2";

  @BeforeEach
  @Override
  public void setUp() throws Exception {
    super.setUp();

    for (StrictIndexMappingCreator index : ALL_INDICES) {
      createOptimizeIndexWithTypeAndVersion(
        index,
        index.getIndexName(),
        index.getVersion() - 1
      );
    }
    setMetadataIndexVersionWithType(FROM_VERSION, METADATA_INDEX.getIndexName());

    executeBulk("steps/report_data/26-single-decision-report-bulk");
    executeBulk("steps/report_data/26-single-process-report-bulk");
    executeBulk("steps/alert_data/26-orphaned-alert-bulk");
  }

  @Test
  public void deleteOrphanedAlerts() {
    //given
    final UpgradePlan upgradePlan = new UpgradeFrom26To27().buildUpgradePlan();

    // when
    upgradePlan.execute();
    List<String> allAlertIds = getAllAlerts();

    // then
    assertThat(allAlertIds).containsExactlyInAnyOrder(expectedAlertId1, expectedAlertId2);
  }

  @SneakyThrows
  private List<String> getAllAlerts() {
    final SearchResponse searchResponse = prefixAwareClient.search(
      new SearchRequest(ALERT_INDEX.getIndexName()).source(new SearchSourceBuilder().size(10000)),
      RequestOptions.DEFAULT
    );
    return Arrays
      .stream(searchResponse.getHits().getHits())
      .map(doc -> {
        try {
          return objectMapper.readValue(
            doc.getSourceAsString(), AlertDefinitionDto.class
          );
        } catch (IOException e) {
          throw new RuntimeException(e);
        }
      })
      .map(alertDto -> alertDto.getId())
      .collect(toList());
  }

}
