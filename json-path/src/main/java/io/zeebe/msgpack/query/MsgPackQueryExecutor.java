/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.msgpack.query;

import io.zeebe.msgpack.filter.MsgPackFilter;
import io.zeebe.msgpack.spec.MsgPackCodes;
import io.zeebe.msgpack.spec.MsgPackToken;
import io.zeebe.msgpack.spec.MsgPackType;
import io.zeebe.util.allocation.HeapBufferAllocator;
import io.zeebe.util.collection.CompactList;
import org.agrona.BitUtil;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

public final class MsgPackQueryExecutor implements MsgPackTokenVisitor {

  protected static final int MAX_TRAVERSAL_DEPTH = 30;
  protected static final int MAX_RESULTS = 1000;

  protected static final int RESULT_SIZE = BitUtil.SIZE_OF_INT * 2;
  protected static final int RESULT_POSITION_OFFSET = 0;
  protected static final int RESULT_LENGTH_OFFSET = BitUtil.SIZE_OF_INT;

  protected MsgPackFilter[] filters;
  protected MsgPackFilterContext filterInstances;
  protected int numFilterInstances;

  protected final CompactList matchingPositions;
  protected final UnsafeBuffer currentResultView = new UnsafeBuffer(0, 0);
  protected final UnsafeBuffer resultWriteBuffer = new UnsafeBuffer(new byte[RESULT_SIZE]);

  protected int matchingContainer = -1;
  protected int matchingContainerStartPosition;

  protected final MsgPackTraversalContext context =
      new MsgPackTraversalContext(MAX_TRAVERSAL_DEPTH, BitUtil.SIZE_OF_INT);

  public MsgPackQueryExecutor() {
    this.matchingPositions = new CompactList(RESULT_SIZE, MAX_RESULTS, new HeapBufferAllocator());
  }

  public void init(final MsgPackFilter[] filters, final MsgPackFilterContext filterInstances) {
    this.filters = filters;
    this.filterInstances = filterInstances;
    this.numFilterInstances = filterInstances.size();
    this.matchingPositions.clear();
  }

  @Override
  public void visitElement(final int position, final MsgPackToken currentValue) {
    // count current element
    int currentFilter = 0;

    if (context.hasElements()) {
      context.moveToLastElement();
      context.currentElement(context.currentElement() + 1);
      currentFilter = context.applyingFilter();
    }

    // evaluate filter
    boolean filterMatch = false;
    if (currentFilter >= 0) {
      filterInstances.moveTo(currentFilter);
      final MsgPackFilter filter = filters[filterInstances.filterId()];
      filterMatch = filter.matches(context, filterInstances.dynamicContext(), currentValue);
    }

    // build new context
    final MsgPackType currentValueType = currentValue.getType();
    if (MsgPackType.ARRAY == currentValueType || MsgPackType.MAP == currentValueType) {
      context.appendElement();
      context.currentElement(-1);
      context.numElements(
          currentValueType == MsgPackType.MAP
              ? currentValue.getSize() * 2
              : currentValue.getSize());
      context.applyingFilter(-1);
      context.setIsMap(MsgPackType.MAP == currentValueType);
    }

    // post-process filter match
    if (filterMatch) {
      if (isLastFilter(currentFilter)) {
        if (currentValue.getType().isScalar()) {
          addResult(position, currentValue.getTotalLength());
        } else {
          matchingContainer = this.context.size() - 1;
          matchingContainerStartPosition = position;
        }
      } else {
        context.applyingFilter(currentFilter + 1);
      }
    }

    // destroy context
    while (context.hasElements() && context.currentElement() + 1 >= context.numElements()) {

      if (matchingContainer == context.size() - 1) {
        addResult(
            matchingContainerStartPosition,
            position + currentValue.getTotalLength() - matchingContainerStartPosition);
        matchingContainer = -1;
      }

      context.removeLastElement();
    }
  }

  protected boolean isLastFilter(final int filterIndex) {
    return filterIndex + 1 == numFilterInstances;
  }

  public int numResults() {
    return matchingPositions.size();
  }

  public void moveToResult(final int index) {
    matchingPositions.wrap(index, currentResultView);
  }

  public int currentResultPosition() {
    return currentResultView.getInt(RESULT_POSITION_OFFSET);
  }

  public int currentResultLength() {
    return currentResultView.getInt(RESULT_LENGTH_OFFSET);
  }

  public boolean isCurrentResultAMap(final DirectBuffer document) {
    final byte headerByte = document.getByte(currentResultPosition());
    return MsgPackCodes.isMap(headerByte);
  }

  protected void addResult(final int position, final int length) {
    resultWriteBuffer.putInt(RESULT_POSITION_OFFSET, position);
    resultWriteBuffer.putInt(RESULT_LENGTH_OFFSET, length);
    matchingPositions.add(resultWriteBuffer);
  }
}
