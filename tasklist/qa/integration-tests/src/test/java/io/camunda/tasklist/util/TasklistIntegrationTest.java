/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.util;

import static org.mockito.Mockito.when;

import io.camunda.configuration.UnifiedConfiguration;
import io.camunda.configuration.UnifiedConfigurationHelper;
import io.camunda.tasklist.property.TasklistProperties;
import io.camunda.tasklist.zeebe.PartitionHolder;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;

@ExtendWith(SpringExtension.class)
@SpringBootTest(
    classes = {TestApplication.class, UnifiedConfigurationHelper.class, UnifiedConfiguration.class},
    properties = {
      TasklistProperties.PREFIX + ".importer.startLoadingDataOnStartup = false",
      TasklistProperties.PREFIX + ".archiver.rolloverEnabled = false",
      TasklistProperties.PREFIX + "importer.jobType = testJobType",
      "camunda.webapps.enabled = true",
      "camunda.webapps.default-app = tasklist",
      TasklistProperties.PREFIX + ".zeebe.compatibility.enabled = true",
      "camunda.security.authentication.unprotected-api = true",
      "camunda.security.authorizations.enabled = false"
    },
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles({"tasklist", "standalone", "test"})
public abstract class TasklistIntegrationTest {

  protected OffsetDateTime testStartTime;
  @Autowired private ApplicationContext applicationContext;

  @BeforeEach
  public void before() {
    testStartTime = OffsetDateTime.now();
    new SpringContextHolder().setApplicationContext(applicationContext);
  }

  protected void mockPartitionHolder(final PartitionHolder partitionHolder) {
    final List<Integer> partitions = new ArrayList<>();
    partitions.add(1);
    when(partitionHolder.getPartitionIds()).thenReturn(partitions);
  }
}
