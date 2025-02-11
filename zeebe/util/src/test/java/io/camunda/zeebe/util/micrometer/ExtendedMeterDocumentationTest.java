/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.util.micrometer;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.temporal.ChronoUnit;
import org.junit.Test;

public class ExtendedMeterDocumentationTest {
  @Test
  public void shouldThrowExceptionWhenBucketsExceedLongMax() {
    final var buckets =
        ExtendedMeterDocumentation.exponentialBucketDuration(7, 6, 1023, ChronoUnit.MICROS);
    assertThat(buckets).hasSizeLessThan(1023);
    assertThat(buckets).allMatch(d -> d.toMillis() > 0L);
  }
}
