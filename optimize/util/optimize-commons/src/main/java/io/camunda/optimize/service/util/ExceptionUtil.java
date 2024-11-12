/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.util;

import static io.camunda.optimize.service.db.DatabaseConstants.DECISION_INSTANCE_INDEX_PREFIX;
import static io.camunda.optimize.service.db.DatabaseConstants.DECISION_INSTANCE_MULTI_ALIAS;
import static io.camunda.optimize.service.db.DatabaseConstants.INDEX_NOT_FOUND_EXCEPTION_TYPE;
import static io.camunda.optimize.service.db.DatabaseConstants.PROCESS_INSTANCE_INDEX_PREFIX;
import static io.camunda.optimize.service.db.DatabaseConstants.PROCESS_INSTANCE_MULTI_ALIAS;
import static io.camunda.optimize.service.db.DatabaseConstants.TOO_MANY_BUCKETS_EXCEPTION_TYPE;

import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch._types.ErrorCause;
import io.camunda.optimize.dto.optimize.DefinitionType;
import io.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import java.util.function.Function;
import org.opensearch.client.opensearch._types.OpenSearchException;

public class ExceptionUtil {
  public static boolean isTooManyBucketsException(final RuntimeException e) {
    return isDbExceptionWithMessage(e, msg -> msg.contains(TOO_MANY_BUCKETS_EXCEPTION_TYPE));
  }

  public static boolean isInstanceIndexNotFoundException(final RuntimeException e) {
    return isDbExceptionWithMessage(e, msg -> msg.contains(INDEX_NOT_FOUND_EXCEPTION_TYPE));
  }

  public static boolean isInstanceIndexNotFoundException(
      final DefinitionType type, final RuntimeException e) {
    return isDbExceptionWithMessage(
        e,
        msg ->
            msg.contains(INDEX_NOT_FOUND_EXCEPTION_TYPE)
                && containsInstanceIndexAliasOrPrefix(type, e.getMessage()));
  }

  private static boolean isDbExceptionWithMessage(
      final RuntimeException e, final Function<String, Boolean> messageFilter) {
    if (e instanceof final ElasticsearchException err) {
      final ErrorCause errorCause = err.error().causedBy();
      if (errorCause != null) {
        return messageFilter.apply(errorCause.type());
      } else {
        return messageFilter.apply(err.getMessage());
      }
    } else if (e instanceof OpenSearchException) {
      return messageFilter.apply(e.getMessage());
    } else if (e instanceof OptimizeRuntimeException) {
      if (e.getCause() != null) {
        if (e.getCause()
            instanceof final org.opensearch.client.transport.httpclient5.ResponseException re) {
          return messageFilter.apply(re.getMessage());
        }
      }
      return messageFilter.apply(e.getMessage());
    } else {
      return false;
    }
  }

  private static boolean containsInstanceIndexAliasOrPrefix(
      final DefinitionType type, final String message) {
    switch (type) {
      case PROCESS:
        return message.contains(PROCESS_INSTANCE_INDEX_PREFIX)
            || message.contains(PROCESS_INSTANCE_MULTI_ALIAS);
      case DECISION:
        return message.contains(DECISION_INSTANCE_INDEX_PREFIX)
            || message.contains(DECISION_INSTANCE_MULTI_ALIAS);
      default:
        throw new OptimizeRuntimeException("Unsupported definition type:" + type);
    }
  }
}
