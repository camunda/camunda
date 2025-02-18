/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.spring.client.properties;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

public class CamundaClientLegacyPropertiesMappingsLoaderTest {
  @Test
  void shouldLoadLegacyProperties() {
    final List<CamundaClientLegacyPropertiesMapping> load =
        CamundaClientLegacyPropertiesMappingsLoader.load();
    assertThat(load).isNotEmpty();
  }
}
