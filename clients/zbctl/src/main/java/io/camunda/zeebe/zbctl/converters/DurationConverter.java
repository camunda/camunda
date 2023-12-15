/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.zbctl.converters;

import java.time.Duration;
import picocli.CommandLine.ITypeConverter;

public final class DurationConverter implements ITypeConverter<Duration> {
  @Override
  public Duration convert(final String value) throws Exception {
    return Duration.parse("PT" + value);
  }
}
