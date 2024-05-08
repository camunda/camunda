/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.elasticsearch;

import static org.junit.Assume.assumeFalse;

import io.camunda.operate.archiver.Archiver;
import io.camunda.operate.archiver.ProcessInstancesArchiverJob;
import io.camunda.operate.zeebe.PartitionHolder;
import org.junit.Before;
import org.junit.Rule;
import org.junit.rules.TestName;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;

public class ProcessStatisticsAfterArchivingIT extends ProcessStatisticsIT {

  @Rule public TestName name = new TestName();
  @Autowired private BeanFactory beanFactory;
  @MockBean private PartitionHolder partitionHolder;

  @Before
  public void before() {
    // testing one with archiver, the others keep on parent tests
    assumeFalse(!name.getMethodName().startsWith("testOneProcessStatistics"));
    super.before();
  }

  @Override
  protected void createData(Long processDefinitionKey) {
    super.createData(processDefinitionKey);
    mockPartitionHolder(partitionHolder);
    final Archiver archiver = beanFactory.getBean(Archiver.class);
    final ProcessInstancesArchiverJob archiverJob =
        beanFactory.getBean(
            ProcessInstancesArchiverJob.class, archiver, partitionHolder.getPartitionIds());
    runArchiving(
        archiverJob,
        () -> {
          searchTestRule.refreshSerchIndexes();
          return null;
        });
    searchTestRule.refreshSerchIndexes();
  }
}
