/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.util;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.auth.Authorization;
import io.camunda.zeebe.logstreams.log.LoggedEvent;
import io.camunda.zeebe.protocol.impl.encoding.AuthInfo;
import io.camunda.zeebe.protocol.impl.record.RecordMetadata;
import io.camunda.zeebe.protocol.record.ValueType;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.IntStream;

/**
 * Compares the log bytes of a test scenario with a baseline. A change of more than 10%, up or down,
 * fails.
 *
 * <p>Each byte on the log costs the user disk, backup storage, and throughput. For example, in 8.9
 * the engine copied all token claims to each follow-up record, and the log became two times larger.
 * Thus, find the cause of a failure before you update the baseline. Make sure that each record
 * needs the new data, and that a reader for it exists.
 *
 * <p>Update the baselines, and give the reason in the pull request:
 *
 * <pre>
 * ./mvnw verify -pl zeebe/engine -Dtest='*RecordSizeBaselineTest' -Dgolden.update=true \
 *   -DskipTests=false -DskipITs -Dquickly
 * </pre>
 */
public final class RecordSizeBaseline {

  private static final String UPDATE_PROPERTY = "golden.update";

  /** Large enough for small encoding changes, small enough for a new field on many records. */
  private static final int TOLERANCE_PERCENT = 10;

  /** Scheduled tasks write these records at times that the scenarios do not control. */
  private static final Set<ValueType> TIMED_VALUE_TYPES =
      Set.of(ValueType.JOB_METRICS_BATCH, ValueType.USAGE_METRIC);

  private RecordSizeBaseline() {}

  /** Returns the last log position of each partition, to measure only the records after it. */
  public static Map<Integer, Long> lastPositions(final EngineRule engine) {
    final var positions = new TreeMap<Integer, Long>();
    for (final int partitionId : engine.getPartitionIds()) {
      positions.put(
          partitionId,
          engine.events(partitionId).map(LoggedEvent::getPosition).reduce((a, b) -> b).orElse(-1L));
    }
    return positions;
  }

  public static void assertMatches(
      final EngineRule engine, final String scenario, final Map<Integer, Long> startPositions) {
    final var actual = measure(engine, startPositions);
    if (Boolean.getBoolean(UPDATE_PROPERTY)) {
      writeBaseline(scenario, actual);
      return;
    }
    assertThat(changes(readBaseline(scenario), actual))
        .as(
            "The log bytes of '%s' changed by more than %d%%. Read the Javadoc of %s before you"
                + " update the baseline",
            scenario, TOLERANCE_PERCENT, RecordSizeBaseline.class.getSimpleName())
        .isEmpty();
  }

  /** Claims of an OIDC client with a realistic size, as the follow-up records copy them. */
  public static AuthInfo oidcClaims(final String username) {
    final var tokenClaims = new TreeMap<String, Object>();
    tokenClaims.put("iss", "https://idp.example.com/realms/camunda-platform");
    tokenClaims.put("sub", "8f14e45f-ceea-467f-a0e6-3c2d1b9f7a10");
    tokenClaims.put("aud", List.of("orchestration-api", "account"));
    tokenClaims.put("azp", "orchestration");
    tokenClaims.put("exp", 1_800_000_000L);
    tokenClaims.put("iat", 1_799_999_700L);
    tokenClaims.put("auth_time", 1_799_999_700L);
    tokenClaims.put("jti", "onrtac:5b8e6c1d-2f3a-4b7c-9d8e-1f2a3b4c5d6e");
    tokenClaims.put("typ", "Bearer");
    tokenClaims.put("sid", "c3d4e5f6-a7b8-4c9d-8e0f-1a2b3c4d5e6f");
    tokenClaims.put("acr", "1");
    tokenClaims.put("scope", "openid profile email");
    tokenClaims.put("email_verified", true);
    tokenClaims.put("name", "Demo User");
    tokenClaims.put("preferred_username", username);
    tokenClaims.put("given_name", "Demo");
    tokenClaims.put("family_name", "User");
    tokenClaims.put("email", "demo@example.com");
    tokenClaims.put(
        "realm_access",
        Map.of("roles", List.of("default-roles-camunda-platform", "offline_access", "uma")));
    tokenClaims.put(
        "resource_access",
        Map.of("account", Map.of("roles", List.of("manage-account", "view-profile"))));
    tokenClaims.put("groups", groups());

    final var claims = new TreeMap<String, Object>();
    claims.put(Authorization.AUTHORIZED_USERNAME, username);
    claims.put(Authorization.USER_GROUPS_CLAIMS, groups());
    claims.put(Authorization.USER_TOKEN_CLAIMS, tokenClaims);
    return AuthInfo.ofClaims(claims);
  }

  private static List<String> groups() {
    return IntStream.rangeClosed(1, 10).mapToObj(i -> "/camunda/department-" + i).toList();
  }

  private static Map<String, Size> measure(
      final EngineRule engine, final Map<Integer, Long> startPositions) {
    final var sizes = new TreeMap<String, Size>();
    final var metadata = new RecordMetadata();
    startPositions.forEach(
        (partitionId, startPosition) ->
            engine
                .events(partitionId)
                .filter(event -> event.getPosition() > startPosition)
                .forEach(
                    event -> {
                      metadata.reset();
                      event.readMetadata(metadata);
                      if (TIMED_VALUE_TYPES.contains(metadata.getValueType())) {
                        return;
                      }
                      final var group = metadata.getRecordType() + " " + metadata.getValueType();
                      final int bytes = event.getMetadataLength() + event.getValueLength();
                      sizes.merge(group, new Size(1, bytes), Size::plus);
                    }));
    return sizes;
  }

  private static List<String> changes(
      final Map<String, Size> baseline, final Map<String, Size> actual) {
    final var groups = new TreeSet<>(baseline.keySet());
    groups.addAll(actual.keySet());
    final var changes = new ArrayList<String>();
    for (final var group : groups) {
      final var expected = baseline.get(group);
      final var size = actual.get(group);
      if (expected == null
          || size == null
          || size.bytes() < expected.bytes() * (100 - TOLERANCE_PERCENT) / 100
          || size.bytes() > expected.bytes() * (100 + TOLERANCE_PERCENT) / 100) {
        changes.add("%s: baseline %s, now %s".formatted(group, expected, size));
      }
    }
    return changes;
  }

  private static Map<String, Size> readBaseline(final String scenario) {
    try {
      final var sizes = new TreeMap<String, Size>();
      for (final var line : Files.readAllLines(baselinePath(scenario), StandardCharsets.UTF_8)) {
        if (line.isBlank() || line.startsWith("#")) {
          continue;
        }
        final var fields = line.split(" ");
        sizes.put(
            fields[0] + " " + fields[1],
            new Size(Integer.parseInt(fields[2]), Long.parseLong(fields[3])));
      }
      return sizes;
    } catch (final IOException e) {
      throw new UncheckedIOException("Failed to read the baseline " + baselinePath(scenario), e);
    }
  }

  private static void writeBaseline(final String scenario, final Map<String, Size> sizes) {
    final var lines = new ArrayList<String>();
    lines.add("# Written by RecordSizeBaseline. Do not edit by hand.");
    lines.add("# <record type> <value type> <record count> <bytes of metadata and value>");
    sizes.forEach((group, size) -> lines.add(group + " " + size.count() + " " + size.bytes()));
    try {
      Files.createDirectories(baselinePath(scenario).getParent());
      Files.write(baselinePath(scenario), lines, StandardCharsets.UTF_8);
    } catch (final IOException e) {
      throw new UncheckedIOException("Failed to write the baseline " + baselinePath(scenario), e);
    }
  }

  /** Surefire runs the tests in the module directory. */
  private static Path baselinePath(final String scenario) {
    return Path.of("src", "test", "resources", "record-size", "golden", scenario + ".golden");
  }

  private record Size(int count, long bytes) {
    Size plus(final Size other) {
      return new Size(count + other.count, bytes + other.bytes);
    }

    @Override
    public String toString() {
      return count + " records, " + bytes + " bytes";
    }
  }
}
