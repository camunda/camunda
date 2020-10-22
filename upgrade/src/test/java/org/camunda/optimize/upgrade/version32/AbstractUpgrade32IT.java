/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.upgrade.version32;

import org.camunda.optimize.service.es.schema.index.DecisionDefinitionIndex;
import org.camunda.optimize.service.es.schema.index.ProcessDefinitionIndex;
import org.camunda.optimize.service.es.schema.index.events.EventProcessDefinitionIndex;
import org.camunda.optimize.upgrade.AbstractUpgradeIT;
import org.junit.jupiter.api.BeforeEach;

import java.util.Arrays;

public abstract class AbstractUpgrade32IT extends AbstractUpgradeIT {

  protected static final ProcessDefinitionIndex PROCESS_DEFINITION_INDEX = new ProcessDefinitionIndex();
  protected static final DecisionDefinitionIndex DECISION_DEFINITION_INDEX = new DecisionDefinitionIndex();
  protected static final EventProcessDefinitionIndex EVENT_PROCESS_DEFINITION_INDEX = new EventProcessDefinitionIndex();

  @BeforeEach
  @Override
  public void setUp() throws Exception {
    super.setUp();
    initSchema(Arrays.asList(
      METADATA_INDEX,
      PROCESS_DEFINITION_INDEX,
      DECISION_DEFINITION_INDEX,
      EVENT_PROCESS_DEFINITION_INDEX
    ));
  }

}
