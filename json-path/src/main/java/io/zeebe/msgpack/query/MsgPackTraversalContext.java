/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.msgpack.query;

import org.agrona.BitUtil;

public class MsgPackTraversalContext extends AbstractDynamicContext {

  protected static final int CURRENT_ELEMENT_OFFSET = 0;
  protected static final int NUM_ELEMENTS_OFFSET = BitUtil.SIZE_OF_INT;
  protected static final int APPLYING_FILTER_OFFSET = BitUtil.SIZE_OF_INT * 2;
  protected static final int CONTAINER_TYPE_OFFSET = BitUtil.SIZE_OF_INT * 3;

  protected static final int STATIC_ELEMENT_SIZE = BitUtil.SIZE_OF_INT * 4;

  public MsgPackTraversalContext(int maxTraversalDepth, int dynamicContextSize) {
    super(maxTraversalDepth, STATIC_ELEMENT_SIZE, dynamicContextSize);
  }

  public int currentElement() {
    return cursorView.getInt(CURRENT_ELEMENT_OFFSET);
  }

  public void currentElement(int newValue) {
    cursorView.putInt(CURRENT_ELEMENT_OFFSET, newValue);
  }

  public int numElements() {
    return cursorView.getInt(NUM_ELEMENTS_OFFSET);
  }

  public void numElements(int newValue) {
    cursorView.putInt(NUM_ELEMENTS_OFFSET, newValue);
  }

  public int applyingFilter() {
    return cursorView.getInt(APPLYING_FILTER_OFFSET);
  }

  public void applyingFilter(int newValue) {
    cursorView.putInt(APPLYING_FILTER_OFFSET, newValue);
  }

  public boolean isMap() {
    return cursorView.getInt(CONTAINER_TYPE_OFFSET) == 0;
  }

  public void setIsMap(boolean isMap) {
    cursorView.putInt(CONTAINER_TYPE_OFFSET, isMap ? 0 : 1);
  }
}
