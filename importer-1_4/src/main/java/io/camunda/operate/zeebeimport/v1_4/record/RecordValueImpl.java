/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package io.camunda.operate.zeebeimport.v1_4.record;

import io.camunda.zeebe.protocol.record.RecordValue;

public abstract class RecordValueImpl implements RecordValue {

  @Override
  public String toJson() {
    throw new UnsupportedOperationException("toJson operation is not supported");
  }

}
