/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.util;

import io.camunda.operate.conditions.DatabaseInfo;
import org.junit.BeforeClass;

import static org.junit.Assume.assumeTrue;


public abstract class OpensearchOperateAbstractIT extends OperateAbstractIT {
  @BeforeClass
  public static void beforeClass() {
    assumeTrue(DatabaseInfo.isOpensearch());
  }
}
