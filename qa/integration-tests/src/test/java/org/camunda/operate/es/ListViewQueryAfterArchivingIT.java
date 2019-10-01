/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.es;

import org.camunda.operate.zeebeimport.PartitionHolder;
import org.camunda.operate.zeebeimport.archiver.Archiver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;

public class ListViewQueryAfterArchivingIT extends ListViewQueryIT {

  @MockBean
  private PartitionHolder partitionHolder;

  @Autowired
  private Archiver archiver;

  @Override
  protected void createData() {
    super.createData();
    mockPartitionHolder(partitionHolder);
    runArchiving(archiver);
  }

}
