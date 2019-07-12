/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.msgpack.mapping;

import io.zeebe.msgpack.jsonpath.JsonPathQuery;
import io.zeebe.msgpack.query.MsgPackQueryExecutor;
import io.zeebe.msgpack.query.MsgPackTraverser;
import org.agrona.DirectBuffer;

/**
 * Represents an message pack document extractor.
 *
 * <p>The extractor can wrap a message pack document, which is stored in a {@link DirectBuffer} and
 * extract parts of this wrapped document with help of the given {@link Mapping} objects. The
 * extracted parts are stored in a {@link MsgPackTree} object, which is returned after calling
 * {@link #extract(Mapping...)}.
 *
 * <p>It is also possible that the extractor wraps an already existing {@link MsgPackTree} object
 * and a message pack document on which the extracting should be done. The extracted parts are
 * stored in the wrapped tree. This means nodes can be added or replaced in the wrapped message pack
 * tree.
 *
 * <p>A {@link Mapping} consist of a source json query and a target json path mapping. The source
 * json query must match on the wrapped message pack document. The matching value will be stored in
 * the resulting {@link MsgPackTree} object, the tree is either the wrapped tree or a tree which
 * only consists of the extracted values.
 *
 * <p>The resulting {@link MsgPackTree} consist of nodes for each part in the target json path
 * mapping and leafs, which corresponds to the leaf in the target json path mapping. These leafs
 * contains the values of the matched source json query, form the wrapped message pack document.
 *
 * <p>Example:
 *
 * <pre>
 * {@code Wrapped document:
 *  {
 *     "sourceObject":{
 *         "foo":"bar"
 *     },
 *     "value1":1
 *  }
 * }
 * </pre>
 *
 * <pre>
 * {@code Mappings:
 *  $.sourceObject -> $.targetObject.value1
 *  $.value1 -> $.newValue1
 * }
 * </pre>
 *
 * <pre>{@code
 * Then the resulting tree will look like the following:
 *
 *           $
 *        /     \
 *  newValue1   targetObject
 *    |           \
 *    1           value1
 *                 \
 *                 {"foo":"bar"}
 * }</pre>
 */
public final class MsgPackDocumentExtractor {
  public static final String EXCEPTION_MSG_MAPPING_DOES_NOT_MATCH = "No data found for query %s.";
  public static final String EXCEPTION_MSG_MAPPING_HAS_MORE_THAN_ONE_MATCHING_SOURCE =
      "JSON path mapping has more than one matching source.";

  private final MappingDiff diff = new MappingDiff();

  private final MsgPackTraverser traverser = new MsgPackTraverser();
  private final MsgPackQueryExecutor queryExecutor = new MsgPackQueryExecutor();

  public MsgPackDiff extract(DirectBuffer document, boolean strictMode, Mapping... mappings) {
    diff.init(mappings, document);
    traverser.wrap(document, 0, document.capacity());

    /*
     * Optimization potential: evaluate all source queries in
     * one pass over the document
     */
    for (int i = 0; i < mappings.length; i++) {

      final Mapping mapping = mappings[i];
      executeLeafMapping(mapping.getSource(), strictMode);

      if (queryExecutor.numResults() > 0) {
        if (mapping.mapsToRootPath()
            && !queryExecutor.isCurrentResultAMap(document)
            && !strictMode) {
          diff.setEmptyMapResult(i);
        } else {
          diff.setResult(
              i, queryExecutor.currentResultPosition(), queryExecutor.currentResultLength());
        }
      } else {
        diff.setNullResult(i);
      }
    }

    return diff;
  }

  /**
   * Executes the given json path query. The matching result is available in the query executor
   * object.
   *
   * @param jsonPathQuery the query which should be executed
   */
  private void executeLeafMapping(JsonPathQuery jsonPathQuery, boolean strictMode) {
    queryExecutor.init(jsonPathQuery.getFilters(), jsonPathQuery.getFilterInstances());

    traverser.reset();
    traverser.traverse(queryExecutor);

    if (queryExecutor.numResults() == 1) {
      queryExecutor.moveToResult(0);
    } else if (queryExecutor.numResults() == 0) {
      final DirectBuffer expression = jsonPathQuery.getExpression();
      if (strictMode) {
        throw new MappingException(
            String.format(
                EXCEPTION_MSG_MAPPING_DOES_NOT_MATCH,
                expression.getStringWithoutLengthUtf8(0, expression.capacity())));
      }
    } else {
      if (strictMode) {
        throw new IllegalStateException(EXCEPTION_MSG_MAPPING_HAS_MORE_THAN_ONE_MATCHING_SOURCE);
      } else {
        queryExecutor.moveToResult(0);
      }
    }
  }
}
