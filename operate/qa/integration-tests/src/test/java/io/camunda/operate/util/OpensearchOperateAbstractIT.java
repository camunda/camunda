/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.util;

import static org.assertj.core.api.Assumptions.assumeThat;

import io.camunda.operate.conditions.DatabaseInfo;
import org.junit.BeforeClass;

public abstract class OpensearchOperateAbstractIT extends OperateAbstractIT {
  @BeforeClass
  public static void beforeClass() {
    assumeThat(DatabaseInfo.isOpensearch()).isTrue();
  }
}
