/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.elasticsearch;

import io.camunda.operate.archiver.Archiver;
import io.camunda.operate.archiver.ProcessInstancesArchiverJob;
import io.camunda.operate.zeebe.PartitionHolder;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;

public class ListViewQueryAfterArchivingIT extends ListViewQueryIT {

  @MockBean private PartitionHolder partitionHolder;

  @Autowired private Archiver archiver;

  @Autowired private BeanFactory beanFactory;

  @Override
  protected void createData() {
    super.createData();
    mockPartitionHolder(partitionHolder);
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
