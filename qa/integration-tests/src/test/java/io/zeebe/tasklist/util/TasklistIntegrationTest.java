/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package io.zeebe.tasklist.util;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.Before;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.TestExecutionListeners;
import org.springframework.test.context.junit4.SpringRunner;
import io.zeebe.tasklist.property.TasklistProperties;
import io.zeebe.tasklist.webapp.security.UserService;
import io.zeebe.tasklist.zeebe.PartitionHolder;
import static org.mockito.Mockito.when;

@RunWith(SpringRunner.class)
@SpringBootTest(
  classes = {TestApplication.class},
  properties = { TasklistProperties.PREFIX + ".importer.startLoadingDataOnStartup = false",
    TasklistProperties.PREFIX + ".archiver.rolloverEnabled = false"},
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestExecutionListeners(listeners = DependencyInjectionTestExecutionListener.class, mergeMode = TestExecutionListeners.MergeMode.MERGE_WITH_DEFAULTS)
public abstract class TasklistIntegrationTest {

  public static final String DEFAULT_USER = "testuser";

  protected OffsetDateTime testStartTime;

  @MockBean
  protected UserService userService;

  @Before
  public void before() {
    testStartTime = OffsetDateTime.now();
    when(userService.getCurrentUsername()).thenReturn(DEFAULT_USER);
  }
  
  protected void mockPartitionHolder(PartitionHolder partitionHolder) {
    List<Integer> partitions = new ArrayList<>();
    partitions.add(1);
    when(partitionHolder.getPartitionIds()).thenReturn(partitions);
  }
}
