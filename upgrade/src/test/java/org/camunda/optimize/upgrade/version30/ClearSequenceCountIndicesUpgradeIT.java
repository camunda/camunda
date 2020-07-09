/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.upgrade.version30;

import lombok.SneakyThrows;
import org.camunda.optimize.dto.optimize.query.event.EventSequenceCountDto;
import org.camunda.optimize.service.es.schema.index.events.EventSequenceCountIndex;
import org.camunda.optimize.upgrade.main.impl.UpgradeFrom30To31;
import org.camunda.optimize.upgrade.plan.UpgradePlan;
import org.camunda.optimize.upgrade.version30.indices.EventSequenceCountIndexV2;
import org.elasticsearch.action.admin.indices.refresh.RefreshRequest;
import org.elasticsearch.client.RequestOptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.EXTERNAL_EVENTS_INDEX_SUFFIX;

public class ClearSequenceCountIndicesUpgradeIT extends AbstractUpgrade30IT {

  private static final String CAMUNDA_DEFINITION_KEY_ONE = "invoice";
  private static final String CAMUNDA_DEFINITION_KEY_TWO = "reviewinvoice";

  @BeforeEach
  @Override
  public void setUp() throws Exception {
    super.setUp();

    createSequenceCountIndexForDefinitionKey(CAMUNDA_DEFINITION_KEY_ONE);
    createSequenceCountIndexForDefinitionKey(CAMUNDA_DEFINITION_KEY_TWO);
    createSequenceCountIndexForDefinitionKey(EXTERNAL_EVENTS_INDEX_SUFFIX);

    upgradeDependencies.getEsClient()
      .getHighLevelClient()
      .indices()
      .refresh(new RefreshRequest(), RequestOptions.DEFAULT);

    executeBulk("steps/3.0/sequence_counts/30-sequence_counts-bulk");
  }

  @SneakyThrows
  @Test
  public void allSequenceCountIndicesGetCleared() {
    // given
    final UpgradePlan upgradePlan = new UpgradeFrom30To31().buildUpgradePlan();
    assertThat(getAllDocumentsForKey(CAMUNDA_DEFINITION_KEY_ONE)).hasSize(1);
    assertThat(getAllDocumentsForKey(CAMUNDA_DEFINITION_KEY_TWO)).hasSize(1);
    assertThat(getAllDocumentsForKey(EXTERNAL_EVENTS_INDEX_SUFFIX)).hasSize(1);

    // when
    upgradePlan.execute();

    // then
    assertThat(getAllDocumentsForKey(CAMUNDA_DEFINITION_KEY_ONE)).isEmpty();
    assertThat(getAllDocumentsForKey(CAMUNDA_DEFINITION_KEY_TWO)).isEmpty();
    assertThat(getAllDocumentsForKey(EXTERNAL_EVENTS_INDEX_SUFFIX)).isEmpty();
  }

  private List<EventSequenceCountDto> getAllDocumentsForKey(String definitionKey) {
    return getAllDocumentsOfIndexAs(
      new EventSequenceCountIndex(definitionKey).getIndexName(),
      EventSequenceCountDto.class
    );
  }

  @SneakyThrows
  private void createSequenceCountIndexForDefinitionKey(final String definitionKey) {
    createOptimizeIndexWithTypeAndVersion(
      new EventSequenceCountIndexV2(definitionKey),
      EventSequenceCountIndexV2.VERSION
    );
  }

}
