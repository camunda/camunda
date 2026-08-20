/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize;

import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import java.util.Locale;
import org.apache.commons.lang3.StringUtils;
import org.opensearch.client.opensearch._types.OpenSearchException;

public enum ErrorType {
  TOO_MANY_BUCKETS("too_many_buckets"),
  INDEX_NOT_FOUND("index_not_found"),
  VERSION_CONFLICT("version_conflict"),
  SEARCH_CONTEXT_MISSING("search_context_missing"),
  NESTED_LIMIT_EXCEEDED("nested_limit_exceeded"),
  ELASTICSEARCH_ERROR("elasticsearch_error"),
  OPENSEARCH_ERROR("opensearch_error");
  private final String value;

  ErrorType(final String value) {
    this.value = value;
  }

  public String getValue() {
    return value;
  }

  /**
   * Detects the error type from an exception by analyzing its full cause chain.
   *
   * @param throwable the exception to analyze
   * @return the matching ErrorType, or null if not a tracked error
   */
  public static ErrorType fromException(final Throwable throwable) {
    ErrorType fallback = null;
    for (Throwable t = throwable; t != null; t = t.getCause()) {
      final String msg = t.getMessage();
      if (StringUtils.isNotBlank(msg)) {
        final String lowerMessage = msg.toLowerCase(Locale.ROOT);
        if (lowerMessage.contains("version_conflict")) {
          return VERSION_CONFLICT;
        }
        if (lowerMessage.contains("index_not_found")) {
          return INDEX_NOT_FOUND;
        }
        if (lowerMessage.contains("nested") && lowerMessage.contains("limit")) {
          return NESTED_LIMIT_EXCEEDED;
        }
      }
      if (fallback == null) {
        if (t instanceof ElasticsearchException) {
          fallback = ELASTICSEARCH_ERROR;
        } else if (t instanceof OpenSearchException) {
          fallback = OPENSEARCH_ERROR;
        }
      }
    }
    return fallback;
  }
}
