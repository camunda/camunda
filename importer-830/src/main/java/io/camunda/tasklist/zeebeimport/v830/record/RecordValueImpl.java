/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.tasklist.zeebeimport.v830.record;

import io.camunda.zeebe.protocol.record.RecordValue;

public abstract class RecordValueImpl implements RecordValue {

  @Override
  public String toJson() {
    throw new UnsupportedOperationException("toJson operation is not supported");
  }
}
