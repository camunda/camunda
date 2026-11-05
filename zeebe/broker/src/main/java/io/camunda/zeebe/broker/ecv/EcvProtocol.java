/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.ecv;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.atomix.cluster.Member;
import io.camunda.zeebe.broker.ecv.EcvApi.EcvState;
import io.camunda.zeebe.broker.ecv.UpgradeReadinessRegistry.ComponentReadiness;
import io.camunda.zeebe.util.ecv.CapabilitySet;
import io.camunda.zeebe.util.ecv.PartitionCapabilities.Progress;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Map;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * The ECV data that brokers gossip in their membership properties and send to each other.
 *
 * <p>The membership protocol adds and updates property keys but never removes one. Thus a key that
 * a broker does not write anymore, for example after a rollback to a binary that does not know the
 * key, keeps its old value. Every value that must not outlive the start that wrote it carries the
 * {@code instance} of that start; the readers ignore a value from another start (design section 1).
 * The decoders ignore unknown fields, so a newer binary can add fields.
 */
final class EcvProtocol {
  static final String SUPPORTED_PROPERTY = "ecv.supported";
  static final String READINESS_PROPERTY = "ecv.readiness";
  static final String ENABLED_PROPERTY = "ecv.enabled";

  static final String ENABLED_QUERY_SUBJECT = "ecv-enabled-query";
  static final String COMMIT_SUBJECT = "ecv-commit";
  static final String STATE_SUBJECT = "ecv-state";
  static final String PROGRESS_SUBJECT = "ecv-progress";

  private static final ObjectMapper MAPPER =
      new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

  private EcvProtocol() {}

  static byte[] encode(final Object value) {
    try {
      return MAPPER.writeValueAsBytes(value);
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  static <T> T decode(final byte[] bytes, final Class<T> type) {
    try {
      return MAPPER.readValue(bytes, type);
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  static String encodeProperty(final Object value) {
    try {
      return MAPPER.writeValueAsString(value);
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  static <T> Optional<T> readProperty(final Member member, final String key, final Class<T> type) {
    final var value = member.properties().getProperty(key);
    if (value == null) {
      return Optional.empty();
    }
    try {
      return Optional.of(MAPPER.readValue(value, type));
    } catch (final IOException e) {
      return Optional.empty();
    }
  }

  /** The capabilities that a broker or gateway supports, gossiped by 8.11 and later. */
  record SupportedData(String instance, String supported) {
    CapabilitySet set() {
      return CapabilitySet.decode(supported);
    }
  }

  /** The readiness of the components of a broker, from the start {@code instance}. */
  record ReadinessData(String instance, Map<String, ComponentReadiness> components) {}

  /** The enabled set that a member knows. It is committed, so it is valid after a restart too. */
  record EnabledData(String enabled) {}

  /** A request that only the system partition leader answers. */
  record Request(String requester) {}

  /** The answer of the system partition leader to the self-check of a broker. */
  record EnabledQueryResponse(boolean leader, @Nullable String enabled) {}

  record StateResponse(boolean leader, @Nullable EcvState state) {}

  record ProgressResponse(Map<String, Map<Integer, PartitionProgress>> physicalTenants) {}

  record PartitionProgress(String applied, @Nullable String rejected, @Nullable String reason) {
    static PartitionProgress of(final Progress progress) {
      return new PartitionProgress(
          progress.applied().encode(),
          progress.rejected() == null ? null : progress.rejected().encode(),
          progress.rejectionReason());
    }
  }
}
