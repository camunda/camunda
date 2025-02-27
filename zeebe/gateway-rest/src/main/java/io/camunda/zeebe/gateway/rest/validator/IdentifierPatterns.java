/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.validator;

import java.util.regex.Pattern;

public final class IdentifierPatterns {

  public static final int MAX_LENGTH = 256;

  /** 1 or more alphanumeric characters, '@', '.', or '_'. */
  public static final String USERNAME_REGEX = "[a-zA-Z0-9@._]+";

  /** 1 or more alphanumeric characters. */
  public static final String ID_REGEX = "[a-zA-Z0-9]+";

  public static final Pattern USERNAME_PATTERN = Pattern.compile(USERNAME_REGEX);
  public static final Pattern ID_PATTERN = Pattern.compile(ID_REGEX);
}
