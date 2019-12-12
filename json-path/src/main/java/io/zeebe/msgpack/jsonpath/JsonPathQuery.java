/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.msgpack.jsonpath;

import io.zeebe.msgpack.filter.MsgPackFilter;
import io.zeebe.msgpack.query.MsgPackFilterContext;
import io.zeebe.util.buffer.BufferUtil;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

public final class JsonPathQuery {
  protected static final int MAX_DEPTH = 30;
  protected static final int MAX_FILTER_CONTEXT_LENGTH = 50;
  protected static final int NO_INVALID_POSITION = -1;

  protected final MsgPackFilter[] filters;
  protected final MsgPackFilterContext filterInstances =
      new MsgPackFilterContext(MAX_DEPTH, MAX_FILTER_CONTEXT_LENGTH);

  protected final UnsafeBuffer expressionBuffer = new UnsafeBuffer(0, 0);
  protected final DirectBuffer variableName = new UnsafeBuffer(0, 0);

  protected int invalidPosition;
  protected String errorMessage;

  public JsonPathQuery(final MsgPackFilter[] filters) {
    this.filters = filters;
  }

  public void wrap(final DirectBuffer buffer, final int offset, final int length) {
    filterInstances.clear();
    invalidPosition = NO_INVALID_POSITION;

    expressionBuffer.wrap(buffer, offset, length);
  }

  public MsgPackFilterContext getFilterInstances() {
    return filterInstances;
  }

  public MsgPackFilter[] getFilters() {
    return filters;
  }

  public void invalidate(final int position, final String message) {
    invalidPosition = position;
    errorMessage = message;
  }

  public boolean isValid() {
    return invalidPosition == NO_INVALID_POSITION;
  }

  public int getInvalidPosition() {
    return invalidPosition;
  }

  public String getErrorReason() {
    return errorMessage;
  }

  @Override
  public String toString() {
    return "JsonPathQuery{" + "expression=" + BufferUtil.bufferAsString(expressionBuffer) + '}';
  }

  public DirectBuffer getExpression() {
    return expressionBuffer;
  }

  public DirectBuffer getVariableName() {
    return variableName;
  }

  public void setVariableName(final byte[] topLevelVariable) {
    variableName.wrap(topLevelVariable);
  }
}
