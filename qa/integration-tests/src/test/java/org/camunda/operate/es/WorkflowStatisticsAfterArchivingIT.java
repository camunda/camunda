/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.es;

import org.camunda.operate.zeebeimport.PartitionHolder;
import org.camunda.operate.zeebeimport.archiver.Archiver;
import org.junit.Before;
import org.junit.Rule;
import org.junit.rules.TestName;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import static org.junit.Assume.assumeFalse;

public class WorkflowStatisticsAfterArchivingIT extends WorkflowStatisticsIT {

  @Autowired
  private Archiver archiver;

  @MockBean
  private PartitionHolder partitionHolder;

  @Rule
  public TestName name = new TestName();

  @Before
  public void before() {
    assumeFalse(name.getMethodName().startsWith("testFail"));
    super.before();
  }

  @Override
  protected void createData(Long workflowKey) {
    super.createData(workflowKey);
    mockPartitionHolder(partitionHolder);
    runArchiving(archiver);
  }

}
