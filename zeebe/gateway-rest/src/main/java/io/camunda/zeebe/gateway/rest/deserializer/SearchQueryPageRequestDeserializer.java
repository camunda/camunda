/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.deserializer;

import static io.camunda.zeebe.gateway.rest.validator.ErrorMessages.ERROR_MESSAGE_AT_LEAST_ONE_FIELD;
import static io.camunda.zeebe.gateway.rest.validator.ErrorMessages.ERROR_MESSAGE_ONLY_ONE_FIELD;

import io.camunda.zeebe.gateway.protocol.rest.CursorBackwardPagination;
import io.camunda.zeebe.gateway.protocol.rest.CursorForwardPagination;
import io.camunda.zeebe.gateway.protocol.rest.LimitPagination;
import io.camunda.zeebe.gateway.protocol.rest.OffsetPagination;
import io.camunda.zeebe.gateway.protocol.rest.SearchQueryPageRequest;
import io.camunda.zeebe.gateway.rest.exception.DeserializationException;
import java.util.List;
import java.util.Set;

public class SearchQueryPageRequestDeserializer
    extends AbstractRequestDeserializer<SearchQueryPageRequest> {
  private static final String LIMIT_PAGINATION_FIELD = "limit";
  private static final String OFFSET_PAGINATION_FIELD = "from";
  private static final String AFTER_PAGINATION_KEY = "after";
  private static final String BEFORE_PAGINATION_KEY = "before";
  private static final List<String> SUPPORTED_FIELDS =
      List.of(
          OFFSET_PAGINATION_FIELD,
          AFTER_PAGINATION_KEY,
          BEFORE_PAGINATION_KEY,
          LIMIT_PAGINATION_FIELD);

  @Override
  protected List<String> getSupportedFields() {
    return SUPPORTED_FIELDS;
  }

  @Override
  protected Class<? extends SearchQueryPageRequest> getResultType(final Set<String> presentFields) {
    if (presentFields.contains(OFFSET_PAGINATION_FIELD)) {
      return OffsetPagination.class;
    } else if (presentFields.contains(AFTER_PAGINATION_KEY)) {
      return CursorForwardPagination.class;
    } else if (presentFields.contains(BEFORE_PAGINATION_KEY)) {
      return CursorBackwardPagination.class;
    }
    return LimitPagination.class;
  }

  @Override
  protected void validateFields(final Set<String> presentFields) {
    if (presentFields.isEmpty()) {
      throw new DeserializationException(
          ERROR_MESSAGE_AT_LEAST_ONE_FIELD.formatted(getSupportedFields()));
    }
    if (!presentFields.contains(LIMIT_PAGINATION_FIELD) && presentFields.size() > 1) {
      throw new DeserializationException(
          ERROR_MESSAGE_ONLY_ONE_FIELD.formatted(getErrorMessageParam()));
    }
  }

  private String getErrorMessageParam() {
    return "[%s, %s, %s]"
        .formatted(OFFSET_PAGINATION_FIELD, AFTER_PAGINATION_KEY, BEFORE_PAGINATION_KEY);
  }
}
