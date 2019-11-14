/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.upgrade.from26To27;

import lombok.SneakyThrows;
import org.camunda.optimize.dto.optimize.ProcessInstanceDto;
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
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.PROCESS_INSTANCE_INDEX_NAME;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

public class UpgradeDurationFieldIT extends AbstractUpgradeIT {
  private static final String FROM_VERSION = "2.6.0";

  private final Long EXPECTED_DURATION = 1210359556L;

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

    executeBulk("steps/process_instance/26-process-instance-bulk");
  }

  @Test
  public void renameDurationField() {
    //given
    final UpgradePlan upgradePlan = new UpgradeFrom26To27().buildUpgradePlan();

    // when
    upgradePlan.execute();

    // then
    List<ProcessInstanceDto> processInstances = getAllProcessInstances();
    assertThat(
      processInstances.stream().allMatch((procInst -> procInst.getDuration().equals(EXPECTED_DURATION))),
      is(true)
    );
  }

  @SneakyThrows
  private List<ProcessInstanceDto> getAllProcessInstances() {
    final SearchResponse searchResponse = prefixAwareClient.search(
      new SearchRequest(PROCESS_INSTANCE_INDEX_NAME).source(new SearchSourceBuilder().size(10000)),
      RequestOptions.DEFAULT
    );
    return Arrays
      .stream(searchResponse.getHits().getHits())
      .map(doc -> {
        try {
          return objectMapper.readValue(
            doc.getSourceAsString(), ProcessInstanceDto.class
          );
        } catch (IOException e) {
          throw new RuntimeException(e);
        }
      })
      .collect(toList());
  }
}
