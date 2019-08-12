/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.msgpack.query;

import io.zeebe.msgpack.jsonpath.JsonPathQuery;
import io.zeebe.msgpack.spec.MsgPackReader;
import io.zeebe.msgpack.spec.MsgPackToken;
import io.zeebe.msgpack.spec.MsgPackType;
import java.util.function.Consumer;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

public class MsgPackQueryProcessor {

  private final MsgPackQueryExecutor queryExecutor = new MsgPackQueryExecutor();
  private final MsgPackTraverser traverser = new MsgPackTraverser();
  private final MsgPackReader reader = new MsgPackReader();

  private final QueryResults results = new QueryResults();
  private final QueryResult result = new QueryResult();

  public QueryResults process(final JsonPathQuery query, final DirectBuffer data) {

    queryExecutor.init(query.getFilters(), query.getFilterInstances());

    traverser.wrap(data, 0, data.capacity());
    traverser.traverse(queryExecutor);

    results.wrap(data);
    return results;
  }

  public class QueryResults {

    private DirectBuffer data;

    private void wrap(final DirectBuffer data) {
      this.data = data;
    }

    public int size() {
      return queryExecutor.numResults();
    }

    public QueryResult getSingleResult() {
      if (size() == 1) {
        result.wrap(readToken(0));
        return result;

      } else if (size() == 0) {
        throw new RuntimeException("no result found");

      } else {
        throw new RuntimeException("found more than one result");
      }
    }

    private MsgPackToken readToken(final int index) {
      queryExecutor.moveToResult(index);

      reader.wrap(data, queryExecutor.currentResultPosition(), queryExecutor.currentResultLength());
      return reader.readToken();
    }
  }

  public class QueryResult {

    private final UnsafeBuffer resultBuffer = new UnsafeBuffer();
    private final ArrayResult arrayResult = new ArrayResult();

    private MsgPackToken token;

    private void wrap(final MsgPackToken token) {
      this.token = token;
    }

    public boolean isString() {
      return token.getType() == MsgPackType.STRING;
    }

    public boolean isLong() {
      return token.getType() == MsgPackType.INTEGER;
    }

    public boolean isArray() {
      return token.getType() == MsgPackType.ARRAY;
    }

    public DirectBuffer getString() {
      if (!isString()) {
        throw new RuntimeException(
            String.format("expected STRING but found '%s'", token.getType()));
      }
      return token.getValueBuffer();
    }

    public DirectBuffer getLongAsString() {
      if (!isLong()) {
        throw new RuntimeException(String.format("expected LONG but found '%s'", token.getType()));
      }

      final long key = token.getIntegerValue();
      final String converted = String.valueOf(key);
      resultBuffer.wrap(converted.getBytes());
      return resultBuffer;
    }

    public ArrayResult getArray() {
      if (!isArray()) {
        throw new RuntimeException(String.format("expected ARRAY but found '%s'", token.getType()));
      }

      final int size = token.getSize();
      arrayResult.wrap(size);
      return arrayResult;
    }

    public DirectBuffer getValue() {
      resultBuffer.wrap(reader.getBuffer());
      return resultBuffer;
    }

    public String getType() {
      return token.getType().name();
    }
  }

  public class ArrayResult {

    private final UnsafeBuffer resultBuffer = new UnsafeBuffer();

    private int size;
    private int currentIndex;

    private void wrap(final int size) {
      this.size = size;
      currentIndex = -1;
    }

    public int size() {
      return size;
    }

    public boolean isEmpty() {
      return size == 0;
    }

    public DirectBuffer getElement(final int index) {
      if (index >= size) {
        throw new IndexOutOfBoundsException(String.format("index: %d, size: %d", index, size));

      } else if (currentIndex > index) {
        throw new IllegalStateException(
            String.format("index: %d, current index: %d", index, currentIndex));

      } else if (currentIndex == index) {
        return resultBuffer;
      }

      final int skipValues = (index - currentIndex);
      if (skipValues > 1) {
        reader.skipValues(skipValues - 1);
      }

      currentIndex = index;

      final int offset = reader.getOffset();
      reader.skipValue();
      final int length = reader.getOffset() - offset;

      resultBuffer.wrap(reader.getBuffer(), offset, length);
      return resultBuffer;
    }

    public void forEach(final Consumer<DirectBuffer> consumer) {
      for (int i = 0; i < size; i++) {
        consumer.accept(getElement(i));
      }
    }
  }
}
