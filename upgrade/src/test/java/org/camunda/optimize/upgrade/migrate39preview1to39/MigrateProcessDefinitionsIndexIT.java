/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.upgrade.migrate39preview1to39;

import org.camunda.optimize.service.es.schema.index.ProcessDefinitionIndex;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class MigrateProcessDefinitionsIndexIT extends AbstractUpgrade39preview1IT {

  @Test
  public void existingProcessDefinitionsAreMarkedAsOnboarded() {
    // given
    executeBulk("steps/3.9preview1/39preview1-process-definition.json");

    // when
    performUpgrade();

    // then
    assertThat(getAllDocumentsOfIndex(PROCESS_DEFINITION_INDEX.getIndexName()))
      .hasSize(3)
      .allSatisfy(def -> assertThat(def.getSourceAsMap()).containsEntry(ProcessDefinitionIndex.ONBOARDED, true));
  }

  @Test
  public void existingEventProcessDefinitionsAreMarkedAsOnboarded() {
    // given
    executeBulk("steps/3.9preview1/39preview1-event-process-definition.json");

    // when
    performUpgrade();

    // then
    assertThat(getAllDocumentsOfIndex(EVENT_PROCESS_DEFINITION_INDEX.getIndexName()))
      .hasSize(2)
      .allSatisfy(def -> assertThat(def.getSourceAsMap()).containsEntry(ProcessDefinitionIndex.ONBOARDED, true));
  }
}
