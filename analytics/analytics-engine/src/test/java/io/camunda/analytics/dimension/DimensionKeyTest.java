/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.dimension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Arrays;
import org.junit.jupiter.api.Test;

final class DimensionKeyTest {

  private static final DimensionSchema REGION_GRAIN =
      DimensionSchema.of(
          new DimensionColumn("region", DimensionType.STRING),
          new DimensionColumn("bpmnProcessId", DimensionType.STRING),
          new DimensionColumn("processDefinitionKey", DimensionType.LONG),
          new DimensionColumn("version", DimensionType.INT),
          new DimensionColumn("completedNormally", DimensionType.BOOLEAN));

  @Test
  void shouldRoundTripAllValueTypes() {
    // given
    final DimensionKeyValue codec = new DimensionKeyValue(REGION_GRAIN);
    final DimensionKey key = DimensionKey.of(REGION_GRAIN, "EU", "invoice", 1234L, 3, true);

    // when
    final DimensionKey decoded = codec.fromBytes(codec.toBytes(key));

    // then
    assertThat(decoded).isEqualTo(key);
    assertThat(decoded.get("region")).isEqualTo("EU");
    assertThat(decoded.get("processDefinitionKey")).isEqualTo(1234L);
    assertThat(decoded.get("version")).isEqualTo(3);
    assertThat(decoded.get("completedNormally")).isEqualTo(true);
  }

  @Test
  void shouldRoundTripNullValuesAsUnknownBucket() {
    // given a key with an absent (null) dimension value
    final DimensionKeyValue codec = new DimensionKeyValue(REGION_GRAIN);
    final DimensionKey key = DimensionKey.of(REGION_GRAIN, null, "invoice", 1234L, 3, false);

    // when
    final DimensionKey decoded = codec.fromBytes(codec.toBytes(key));

    // then
    assertThat(decoded).isEqualTo(key);
    assertThat(decoded.get("region")).isNull();
    assertThat(decoded.get("completedNormally")).isEqualTo(false);
  }

  @Test
  void shouldReuseFlyweightAcrossKeys() {
    // given one reused codec instance
    final DimensionKeyValue codec = new DimensionKeyValue(REGION_GRAIN);

    // when two different keys are serialized in turn (reset must clear stale array entries)
    final byte[] first = codec.toBytes(DimensionKey.of(REGION_GRAIN, "EU", "a", 1L, 1, true));
    final DimensionKey second =
        codec.fromBytes(codec.toBytes(DimensionKey.of(REGION_GRAIN, "US", "b", 2L, 2, false)));
    final DimensionKey firstDecoded = codec.fromBytes(first);

    // then each round-trips to its own value, no bleed-through
    assertThat(second).isEqualTo(DimensionKey.of(REGION_GRAIN, "US", "b", 2L, 2, false));
    assertThat(firstDecoded).isEqualTo(DimensionKey.of(REGION_GRAIN, "EU", "a", 1L, 1, true));
  }

  @Test
  void shouldBeValueEqualForMapKeyUse() {
    // given two independently built keys with the same values
    final DimensionKey a = DimensionKey.of(REGION_GRAIN, "EU", "invoice", 1234L, 3, true);
    final DimensionKey b = DimensionKey.of(REGION_GRAIN, "EU", "invoice", 1234L, 3, true);
    final DimensionKey different = DimensionKey.of(REGION_GRAIN, "US", "invoice", 1234L, 3, true);

    // then
    assertThat(a).isEqualTo(b).hasSameHashCodeAs(b);
    assertThat(a).isNotEqualTo(different);
  }

  @Test
  void shouldRejectWrongValueCount() {
    // then
    assertThatThrownBy(() -> DimensionKey.of(REGION_GRAIN, "EU", "invoice"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("expected 5 values");
  }

  @Test
  void shouldRejectValueOfWrongType() {
    // when version (INT) is given a String
    assertThatThrownBy(() -> DimensionKey.of(REGION_GRAIN, "EU", "invoice", 1234L, "three", true))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("version");
  }

  @Test
  void shouldRejectKeyWhoseSchemaMismatchesFlyweight() {
    // given a codec bound to REGION_GRAIN and a key of a different grain
    final DimensionKeyValue codec = new DimensionKeyValue(REGION_GRAIN);
    final DimensionSchema otherGrain =
        DimensionSchema.of(new DimensionColumn("tenantId", DimensionType.STRING));
    final DimensionKey foreign = DimensionKey.of(otherGrain, "acme");

    // then
    assertThatThrownBy(() -> codec.toBytes(foreign))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("does not match flyweight schema");
  }

  @Test
  void shouldRejectDuplicateColumnNames() {
    assertThatThrownBy(
            () ->
                DimensionSchema.of(
                    new DimensionColumn("region", DimensionType.STRING),
                    new DimensionColumn("region", DimensionType.STRING)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("unique");
  }

  @Test
  void shouldReportColumnIndexAndMissingColumn() {
    // then
    assertThat(REGION_GRAIN.indexOf("version")).isEqualTo(3);
    assertThat(REGION_GRAIN.indexOf("nope")).isEqualTo(-1);
    assertThatThrownBy(
            () -> DimensionKey.of(REGION_GRAIN, Arrays.asList("EU", "a", 1L, 1, true)).get("nope"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("no dimension 'nope'");
  }
}
