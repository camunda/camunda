/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.msgpack.query;

import org.agrona.BitUtil;

public class MsgPackFilterContext extends AbstractDynamicContext {

  protected static final int FILTER_ID_OFFSET = 0;

  protected static final int STATIC_ELEMENT_SIZE = BitUtil.SIZE_OF_INT;

  public MsgPackFilterContext(int capacity, int dynamicContextSize) {
    super(capacity, STATIC_ELEMENT_SIZE, dynamicContextSize);
  }

  public int filterId() {
    return cursorView.getInt(FILTER_ID_OFFSET);
  }

  public void filterId(int filterId) {
    cursorView.putInt(FILTER_ID_OFFSET, filterId);
  }
}
