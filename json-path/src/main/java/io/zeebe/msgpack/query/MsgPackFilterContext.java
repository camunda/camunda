/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.msgpack.query;

import org.agrona.BitUtil;

public final class MsgPackFilterContext extends AbstractDynamicContext {

  private static final int FILTER_ID_OFFSET = 0;

  private static final int STATIC_ELEMENT_SIZE = BitUtil.SIZE_OF_INT;

  public MsgPackFilterContext(final int capacity, final int dynamicContextSize) {
    super(capacity, STATIC_ELEMENT_SIZE, dynamicContextSize);
  }

  public int filterId() {
    return cursorView.getInt(FILTER_ID_OFFSET);
  }

  public void filterId(final int filterId) {
    cursorView.putInt(FILTER_ID_OFFSET, filterId);
  }
}
