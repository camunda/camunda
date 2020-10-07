/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.upgrade.migrate31To32;

import org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex;
import org.camunda.optimize.service.es.schema.index.events.EventProcessInstanceIndex;
import org.camunda.optimize.upgrade.main.impl.UpgradeFrom31To32;
import org.camunda.optimize.upgrade.plan.UpgradePlan;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.PROCESS_INSTANCE_INDEX_NAME;

public class ProcessInstanceIncidentsInitializationIT extends AbstractUpgrade31IT {

  @BeforeEach
  @Override
  public void setUp() throws Exception {
    super.setUp();

    executeBulk("steps/3.1/processInstances/31-process-instance-bulk");
  }

  @Test
  public void incidentsListIsInitializedForEngineProcessInstances() {
    // given
    final UpgradePlan upgradePlan = new UpgradeFrom31To32().buildUpgradePlan();

    // when
    upgradePlan.execute();

    // then
    assertThat(getAllDocumentsOfIndex(PROCESS_INSTANCE_INDEX_NAME))
      .hasSize(1)
      .allSatisfy(searchHit -> {
        assertThat(searchHit.getSourceAsMap().get(ProcessInstanceIndex.PROCESS_INSTANCE_ID))
          .isEqualTo("b49fc4c7-0899-11eb-a303-6e208305ef92");
        assertThat(searchHit.getSourceAsMap().get(ProcessInstanceIndex.INCIDENTS))
          .isNotNull()
          .isInstanceOf(List.class)
          .isEqualTo(new ArrayList<>());
      });
  }

  @Test
  public void incidentsListIsInitializedForEventProcessInstances() {
    // given
    final UpgradePlan upgradePlan = new UpgradeFrom31To32().buildUpgradePlan();

    // when
    upgradePlan.execute();

    // then
    assertThat(getAllDocumentsOfIndex(new EventProcessInstanceIndex(EVENT_PROCESS_ID).getIndexName()))
      .hasSize(1)
      .allSatisfy(searchHit -> {
        assertThat(searchHit.getSourceAsMap().get(ProcessInstanceIndex.PROCESS_INSTANCE_ID))
          .isEqualTo("myTraceId1");
        assertThat(searchHit.getSourceAsMap().get(ProcessInstanceIndex.INCIDENTS))
          .isNotNull()
          .isInstanceOf(List.class)
          .isEqualTo(new ArrayList<>());
      });
  }

}
