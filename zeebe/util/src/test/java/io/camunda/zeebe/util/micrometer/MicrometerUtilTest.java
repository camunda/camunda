/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.util.micrometer;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.temporal.ChronoUnit;
import org.junit.Test;

public class MicrometerUtilTest {
  @Test
  public void shouldThrowExceptionWhenBucketsExceedLongMax() {
    final var buckets = MicrometerUtil.exponentialBucketDuration(7, 6, 1023, ChronoUnit.MILLIS);
    assertThat(buckets).hasSizeLessThan(1023);
    assertThat(buckets).allMatch(d -> d.toMillis() > 0L);
  }
}
