/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.upgrade.version30;

import lombok.SneakyThrows;
import org.assertj.core.util.Lists;
import org.camunda.optimize.dto.optimize.query.event.EventSequenceCountDto;
import org.camunda.optimize.service.es.schema.index.events.EventSequenceCountIndex;
import org.camunda.optimize.upgrade.AbstractUpgradeIT;
import org.camunda.optimize.upgrade.main.impl.UpgradeFrom30To31;
import org.camunda.optimize.upgrade.plan.UpgradePlan;
import org.elasticsearch.action.admin.indices.refresh.RefreshRequest;
import org.elasticsearch.client.RequestOptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.EXTERNAL_EVENTS_INDEX_SUFFIX;

public class DeleteCamundaSequenceCountIndicesUpgradeIT extends AbstractUpgradeIT {

  private static final String FROM_VERSION = "3.0.0";

  private static final String CAMUNDA_DEFINITION_KEY_ONE = "invoice";
  private static final String CAMUNDA_DEFINITION_KEY_TWO = "reviewinvoice";

  @BeforeEach
  @Override
  public void setUp() throws Exception {
    super.setUp();

    initSchema(Lists.newArrayList(
      METADATA_INDEX,
      SINGLE_PROCESS_REPORT_INDEX,
      SINGLE_DECISION_REPORT_INDEX,
      COMBINED_REPORT_INDEX,
      TIMESTAMP_BASED_IMPORT_INDEX,
      IMPORT_INDEX_INDEX
    ));
    setMetadataIndexVersion(FROM_VERSION);

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
  public void onlyCamundaSequenceCountIndicesGetDeleted() {
    // given
    final UpgradePlan upgradePlan = new UpgradeFrom30To31().buildUpgradePlan();
    assertThat(getAllDocumentsForKey(CAMUNDA_DEFINITION_KEY_ONE)).hasSize(1);
    assertThat(getAllDocumentsForKey(CAMUNDA_DEFINITION_KEY_TWO)).hasSize(1);
    assertThat(getAllDocumentsForKey(EXTERNAL_EVENTS_INDEX_SUFFIX)).hasSize(1);

    // when
    upgradePlan.execute();

    // then
    assertThat(indexExists(new EventSequenceCountIndex(CAMUNDA_DEFINITION_KEY_ONE).getIndexName())).isFalse();
    assertThat(indexExists(new EventSequenceCountIndex(CAMUNDA_DEFINITION_KEY_TWO).getIndexName())).isFalse();
    assertThat(getAllDocumentsForKey(EXTERNAL_EVENTS_INDEX_SUFFIX)).hasSize(1);
  }

  private List<EventSequenceCountDto> getAllDocumentsForKey(String definitionKey) {
    return getAllDocumentsOfIndex(
      new EventSequenceCountIndex(definitionKey).getIndexName(),
      EventSequenceCountDto.class
    );
  }

  @SneakyThrows
  private void createSequenceCountIndexForDefinitionKey(final String definitionKey) {
    createOptimizeIndexWithTypeAndVersion(new EventSequenceCountIndex(definitionKey), EventSequenceCountIndex.VERSION);
  }

}
