/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.management.cluster;

import com.fasterxml.jackson.annotation.JsonValue;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import io.atomix.cluster.BrokerMemberId;
import io.camunda.zeebe.management.cluster.BrokerId.Integer;
import io.camunda.zeebe.management.cluster.BrokerId.String;
import java.util.Objects;

/**
 * A broker ID that is either an integer (non-zone-aware clusters) or a string (zone-aware clusters,
 * e.g. "zone-a_0").
 *
 * <p>This is used to deserialize BrokerId from openAPI spec, to allow deserializing it from an
 * integer or from a string.
 */
@JsonDeserialize(using = BrokerIdDeserializer.class)
public abstract sealed class BrokerId permits Integer, String {

  public abstract Object value();

  public abstract BrokerMemberId brokerId();

  @Override
  public int hashCode() {
    return Objects.hashCode(value());
  }

  @Override
  public boolean equals(final Object o) {
    if (o instanceof final BrokerId other) {
      return Objects.equals(value(), other.value());
    }
    // allow direct comparison with Integer or String
    return valueEquals(o);
  }

  @Override
  public java.lang.String toString() {
    return java.lang.String.valueOf(value());
  }

  public boolean valueEquals(final Object other) {
    return Objects.equals(value(), other);
  }

  public static BrokerId of(final Object obj) {
    return switch (obj) {
      case final java.lang.Integer integer -> new Integer(integer);
      case final java.lang.String string -> new String(string);
      case final BrokerId brokerId -> brokerId;
      default -> throw new IllegalStateException("Unexpected value: " + obj);
    };
  }

  public static final class Integer extends BrokerId {

    private final int value;

    public Integer(final int value) {
      this.value = value;
    }

    @Override
    @JsonValue
    public java.lang.Integer value() {
      return value;
    }

    @Override
    public BrokerMemberId brokerId() {
      return BrokerMemberId.from(value);
    }
  }

  public static final class String extends BrokerId {

    private final java.lang.String value;

    public String(final java.lang.String value) {
      this.value = value;
    }

    @Override
    @JsonValue
    public java.lang.String value() {
      return value;
    }

    @Override
    public BrokerMemberId brokerId() {
      return BrokerMemberId.from(value);
    }
  }
}
