/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.connect.os;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

public class OpensearchConnectorIT {

  @RegisterExtension private static SearchDBExtension searchDB = SearchDBExtension.create();

  @Test
  void shouldCreateConnectionToAWSOpenSearchAndRetrieveData() throws Exception {
    final var count = searchDB.osClient().count();
    assertThat(count).isNotEqualTo(0);
  }

  @Test
  void shouldCreateConnectionToAWSOpenSearchAndRetrieveDataAsyncClient() throws Exception {

    final var count = searchDB.asyncOsClient().count();
    assertThat(count).isNotEqualTo(0);
  }
}
