/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.msgpack.query;

import io.zeebe.msgpack.spec.MsgPackReader;
import io.zeebe.msgpack.spec.MsgPackToken;
import org.agrona.DirectBuffer;

public final class MsgPackTraverser {

  protected static final int NO_INVALID_POSITION = -1;

  protected String errorMessage;
  protected int invalidPosition;

  protected final MsgPackReader msgPackReader = new MsgPackReader();

  public void wrap(final DirectBuffer buffer, final int offset, final int length) {
    this.msgPackReader.wrap(buffer, offset, length);
    this.invalidPosition = NO_INVALID_POSITION;
    this.errorMessage = null;
  }

  public void reset() {
    msgPackReader.reset();
    this.invalidPosition = NO_INVALID_POSITION;
    this.errorMessage = null;
  }

  /**
   * @param visitor
   * @return true if document could be traversed successfully
   */
  public boolean traverse(final MsgPackTokenVisitor visitor) {
    while (msgPackReader.hasNext()) {
      final int nextTokenPosition = msgPackReader.getOffset();

      final MsgPackToken nextToken;
      try {
        nextToken = msgPackReader.readToken();
      } catch (final Exception e) {
        errorMessage = e.getMessage();
        invalidPosition = nextTokenPosition;
        return false;
      }

      visitor.visitElement(nextTokenPosition, nextToken);
    }

    return true;
  }

  public int getInvalidPosition() {
    return invalidPosition;
  }

  public String getErrorMessage() {
    return errorMessage;
  }
}
