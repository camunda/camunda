/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.validator;

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
}
