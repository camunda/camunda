/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
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
    Archiver archiver = beanFactory.getBean(Archiver.class);
    ProcessInstancesArchiverJob archiverJob =
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
