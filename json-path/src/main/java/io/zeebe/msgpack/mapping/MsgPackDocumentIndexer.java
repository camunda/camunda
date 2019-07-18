/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.msgpack.mapping;

import io.zeebe.msgpack.query.MsgPackTokenVisitor;
import io.zeebe.msgpack.query.MsgPackTraverser;
import io.zeebe.msgpack.spec.MsgPackCodes;
import io.zeebe.msgpack.spec.MsgPackToken;
import java.util.ArrayDeque;
import java.util.Deque;
import org.agrona.DirectBuffer;

/**
 * Represents an message pack document indexer. During the indexing of an existing message pack
 * document an {@link MsgPackTree} object will be constructed, which corresponds to the structure of
 * the message pack document.
 *
 * <p>
 *
 * <p>Example:
 *
 * <pre>{@code
 * Say we have the following json as message pack document:
 * {
 *   "object1":{ "field1": true, "array":[ 1,2,3]},
 *   "field2" : "String"
 * }
 * }</pre>
 *
 * <p>
 *
 * <pre>
 * The {@link #index()} method will return an {@link MsgPackTree} object,
 * which has the following structure:
 * {@code
 *          $
 *        /   \
 *   field2   object1
 *     |     /       \
 * String  field1   array
 *          |      /  |  \
 *         true   1   2   3
 * }
 * </pre>
 *
 * <p>Then this correspond to the following message pack tree structure:
 *
 * <pre>NodeTypes:
 * {@code
 *
 * 1object1 : MAP_NODE
 * 2field1 : LEAF
 * 2array : ARRAY_NODE
 * 1field2: LEAF
 * }
 * </pre>
 *
 * <pre>NodeChildsMap:
 * {@code
 *
 * 1object1: field1, array
 * 2array : 2array1, 2array2, 2array3,
 * }
 * </pre>
 *
 * <pre>LeafMap:
 * {@code
 *
 * 2field1: mapping
 * 2array1: mapping
 * 2array2: mapping
 * 2array3: mapping
 * 1field2: mapping
 * }
 * </pre>
 */
public final class MsgPackDocumentIndexer implements MsgPackTokenVisitor {
  /** The message pack tree which is constructed via the indexing of the message pack document. */
  private final MsgPackTree msgPackTree;

  private final Deque<TokenParseContext> parsingContextStack = new ArrayDeque<>();
  private final MapEntryParseContext mapEntryContext = new MapEntryParseContext();

  /** The traverser which is used to index the message pack document. */
  private final MsgPackTraverser traverser = new MsgPackTraverser();

  private int documentId;

  public MsgPackDocumentIndexer() {
    msgPackTree = new MsgPackTree();
  }

  public MsgPackTree index(DirectBuffer document) {
    clear();

    if (isEmptyOrNil(document)) {
      return msgPackTree;
    }

    parsingContextStack.push(new TokenParseContext(ParsingMode.MAP_ENTRY, "", 1));
    mapEntryContext.currentKey = "$";
    mapEntryContext.parsingMode = MapEntryParsingMode.VALUE;

    documentId = msgPackTree.addDocument(document);
    traverser.wrap(document, 0, document.capacity());

    traverser.traverse(this);
    return msgPackTree;
  }

  private static boolean isEmptyOrNil(DirectBuffer document) {
    return document.capacity() == 0 || MsgPackCodes.NIL == document.getByte(0);
  }

  @Override
  public void visitElement(int position, MsgPackToken currentValue) {

    final TokenParseContext tokenContext = parsingContextStack.peek();
    tokenContext.consumeRepetition();

    if (tokenContext.remainingRepetitions == 0) {
      parsingContextStack.remove();
    }

    switch (tokenContext.parsingMode) {
      case MAP_ENTRY:
        switch (mapEntryContext.parsingMode) {
          case KEY:
            mapEntryContext.currentKey = parseMapKey(currentValue);
            mapEntryContext.parsingMode = MapEntryParsingMode.VALUE;
            break;
          case VALUE:
            parseValue(tokenContext, mapEntryContext.currentKey, position, currentValue);
            mapEntryContext.currentKey = "";
            mapEntryContext.parsingMode = MapEntryParsingMode.KEY;
            break;
          default:
            throw new IllegalStateException(
                "Unexpected map entry parsing mode " + mapEntryContext.parsingMode.name());
        }
        break;
      case ARRAY_ENTRY:
        final int index = tokenContext.repetitions - tokenContext.remainingRepetitions - 1;
        final String key = Integer.toString(index);

        parseValue(tokenContext, key, position, currentValue);

        break;
      default:
        throw new IllegalStateException(
            "Unknown token parsing mode " + tokenContext.parsingMode.name());
    }
  }

  private void parseValue(
      TokenParseContext tokenContext, String key, int valuePosition, MsgPackToken value) {

    switch (value.getType()) {
      case MAP:
        parseMapValue(tokenContext, key, value);
        break;
      case ARRAY:
        parseArrayValue(tokenContext, key, value);
        break;
      default:
        parsePrimitiveValue(tokenContext, key, valuePosition, value);
        break;
    }
  }

  private void parsePrimitiveValue(
      TokenParseContext tokenContext, String key, int valuePosition, MsgPackToken value) {
    msgPackTree.addValueNode(
        tokenContext.parentNodeId, key, documentId, valuePosition, value.getTotalLength());
  }

  private void parseArrayValue(TokenParseContext tokenContext, String key, MsgPackToken value) {
    final String arrayNodeId = msgPackTree.addArrayNode(tokenContext.parentNodeId, key);
    final int arrayElements = value.getSize();

    if (arrayElements > 0) {
      parsingContextStack.push(
          new TokenParseContext(ParsingMode.ARRAY_ENTRY, arrayNodeId, arrayElements));
    }
  }

  private void parseMapValue(TokenParseContext tokenContext, String key, MsgPackToken value) {
    final String nodeId = msgPackTree.addMapNode(tokenContext.parentNodeId, key);
    final int mapElements = value.getSize();

    if (mapElements > 0) {
      parsingContextStack.push(
          new TokenParseContext(ParsingMode.MAP_ENTRY, nodeId, mapElements * 2));
    }
  }

  private String parseMapKey(MsgPackToken currentValue) {
    final DirectBuffer valueBuffer = currentValue.getValueBuffer();

    return valueBuffer.getStringWithoutLengthUtf8(0, valueBuffer.capacity());
  }

  /** Clears the preprocessor and resets to the initial state. */
  private void clear() {
    parsingContextStack.clear();
    msgPackTree.clear();
  }

  static class MapEntryParseContext {
    private MapEntryParsingMode parsingMode;
    private String currentKey;
  }

  static class TokenParseContext {
    final ParsingMode parsingMode;
    final String parentNodeId;
    final int repetitions;

    int remainingRepetitions;

    TokenParseContext(ParsingMode parsingMode, String parentNodeId, int repetitions) {
      this.parsingMode = parsingMode;
      this.parentNodeId = parentNodeId;
      this.repetitions = repetitions;
      this.remainingRepetitions = repetitions;
    }

    void consumeRepetition() {
      remainingRepetitions--;
    }
  }

  enum ParsingMode {
    MAP_ENTRY,
    ARRAY_ENTRY
  }

  enum MapEntryParsingMode {
    KEY,
    VALUE
  }
}
