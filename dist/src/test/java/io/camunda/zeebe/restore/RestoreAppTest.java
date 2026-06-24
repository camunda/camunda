/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.restore;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.configuration.beans.BrokerBasedProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles("restore")
@SpringBootTest(
    classes = RestoreApp.class,
    properties = {
      "camunda.data.backup.store=filesystem",
      "camunda.data.backup.filesystem.basepath=/tmp",
      "camunda.cluster.node-id=26",
      "backupId=27"
    })
public class RestoreAppTest {

  @Autowired private BrokerBasedProperties brokerBasedProperties;

  @Test
  void testUnifiedConfigurationClassesLoadSuccessfully() {
    assertThat(brokerBasedProperties).isNotNull();
    assertThat(brokerBasedProperties.getCluster()).isNotNull();
    assertThat(brokerBasedProperties.getCluster().getNodeId()).isEqualTo(26);
  }
}
