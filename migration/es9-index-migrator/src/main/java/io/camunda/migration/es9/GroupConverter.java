/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.es9;

import java.util.Locale;
import picocli.CommandLine.ITypeConverter;
import picocli.CommandLine.TypeConversionException;

/** Accepts the lower-case ids that the termination message uses. */
final class GroupConverter implements ITypeConverter<Group> {

  @Override
  public Group convert(final String value) {
    try {
      return Group.valueOf(value.toUpperCase(Locale.ROOT));
    } catch (final IllegalArgumentException e) {
      throw new TypeConversionException("expected oc or optimize but was '" + value + "'");
    }
  }
}
