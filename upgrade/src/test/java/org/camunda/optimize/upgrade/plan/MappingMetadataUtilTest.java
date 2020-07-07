/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.upgrade.plan;

import org.camunda.optimize.service.es.schema.IndexMappingCreator;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.upgrade.util.MappingMetadataUtil.getAllNonDynamicMappings;

public class MappingMetadataUtilTest {

  @Test
  public void testGetMappings() {
    final List<IndexMappingCreator> mappings = getAllNonDynamicMappings();

    assertThat(mappings).hasSize(25);
  }
}
