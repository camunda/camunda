/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.util;

import static org.junit.Assume.assumeTrue;

import io.camunda.operate.conditions.DatabaseInfoProvider;
import org.junit.Before;
import org.springframework.beans.factory.annotation.Autowired;

public abstract class OpensearchOperateAbstractIT extends OperateAbstractIT {

  @Autowired private DatabaseInfoProvider databaseInfoProvider;

  @Before
  public void beforeClass() {
    assumeTrue(databaseInfoProvider.isOpensearch());
  }
}
