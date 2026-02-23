/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mapping.http.validator;

public final class ErrorMessages {

  public static final String ERROR_MESSAGE_DATE_PARSING =
      "The provided %s '%s' cannot be parsed as a date according to RFC 3339, section 5.6";
  public static final String ERROR_MESSAGE_EMPTY_UPDATE_CHANGESET =
      """
      No update data provided. Provide at least an "action" or a non-null value \
      for a supported attribute in the "changeset\"""";
  public static final String ERROR_MESSAGE_EMPTY_ATTRIBUTE = "No %s provided";
  public static final String ERROR_MESSAGE_INVALID_ATTRIBUTE_VALUE =
      "The value for %s is '%s' but must be %s";
  public static final String ERROR_SORT_FIELD_MUST_NOT_BE_NULL = "Sort field must not be null";
  public static final String ERROR_UNKNOWN_SORT_BY = "Unknown sortBy: %s";
  public static final String ERROR_SEARCH_UNKNOWN_PAGE_TYPE =
      "Page type is unknown, please provide valid pagination parameters";
  public static final String ERROR_MESSAGE_AT_LEAST_ONE_FIELD = "At least one of %s is required";
  public static final String ERROR_MESSAGE_INVALID_TENANT =
      "Expected to handle request %s with tenant identifier '%s', but %s";
  public static final String ERROR_MESSAGE_INVALID_TENANTS =
      "Expected to handle request %s with tenant identifiers %s, but %s";
  public static final String ERROR_MESSAGE_MISSING_TENANT =
      "Expected to handle request %s with multi-tenancy enabled, but no tenant identifier was provided";
  public static final String ERROR_MESSAGE_ONLY_ONE_FIELD = "Only one of %s is allowed";
  public static final String ERROR_MESSAGE_INVALID_EMAIL = "The provided email '%s' is not valid";
  public static final String ERROR_MESSAGE_ALL_REQUIRED_FIELD = "All %s are required";
  public static final String ERROR_MESSAGE_TOO_MANY_CHARACTERS =
      "The provided %s exceeds the limit of %d characters";
  public static final String ERROR_MESSAGE_ILLEGAL_CHARACTER =
      "The provided %s contains illegal characters. It must match the pattern '%s'";
  public static final String ERROR_MESSAGE_NULL_VARIABLE_NAME = "Variable name must not be null";
  public static final String ERROR_MESSAGE_NULL_VARIABLE_VALUE = "Variable value must not be null";
  public static final String ERROR_MESSAGE_INVALID_KEY_FORMAT =
      "The provided %s '%s' is not a valid key. Expected a numeric value. Did you pass an entity id instead of an entity key?";
  public static final String ERROR_MESSAGE_INVALID_TAG_FORMAT =
      "The provided tag '%s' is not valid. %s";
  public static final String ERROR_MESSAGE_INVALID_TAGS_COUNT =
      "The provided number of tags '%s' is not supported. Ensure to not add more than %s tags.";
}
