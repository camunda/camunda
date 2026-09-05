/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration.physicaltenants;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import io.camunda.configuration.Camunda;
import io.camunda.configuration.Exporter;
import io.camunda.configuration.UnifiedConfigurationException;
import io.camunda.configuration.UnifiedConfigurationHelper;
import io.camunda.zeebe.exporter.api.ExporterConfigMerger;
import io.camunda.zeebe.exporter.api.ExporterConfigMerger.ExporterIsolationClaim;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

/**
 * Unit tests for the {@link GenericExporterIsolationValidation} cross-tenant rule: no two physical
 * tenants may have generic exporters that claim the same {@code (domain, key)} resource. Claims are
 * supplied by injected fake {@link ExporterConfigMerger}s — the concrete exporter modules are not
 * on the configuration module's test classpath, and the grouping logic under test is
 * domain-agnostic anyway. Two representative domains are exercised: an index write target and a
 * lifecycle policy.
 *
 * <p>That {@link PhysicalTenantResolver} actually runs this rule, and runs it after narrowing, is
 * pinned separately in {@code PhysicalTenantResolverTest}.
 */
class GenericExporterIsolationValidationTest {

  private static final String INDEX = "index-write-target";
  private static final String LIFECYCLE = "lifecycle-policy";
  private static final String MERGED_CLASS = "io.camunda.zeebe.exporter.ElasticsearchExporter";
  private static final String UNMERGED_CLASS = "com.acme.CustomExporter";

  private final GenericExporterIsolationValidation validation =
      new GenericExporterIsolationValidation(() -> List.of(fakeMerger(MERGED_CLASS)));

  @BeforeEach
  void setUp() {
    UnifiedConfigurationHelper.setCustomEnvironment(new MockEnvironment());
  }

  @AfterEach
  void tearDown() {
    UnifiedConfigurationHelper.setCustomEnvironment(null);
  }

  @Test
  void shouldRejectTwoTenantsClaimingTheSameIndexTarget() {
    // given two tenants whose generic exporters claim the same index write target
    final Map<String, Camunda> resolved =
        tenants(
            "tenanta", camunda(exporter("esaudit", MERGED_CLASS, "es|shared", null)),
            "tenantb", camunda(exporter("esaudit", MERGED_CLASS, "es|shared", null)));

    // when / then rejected, naming both tenants and the shared resource
    assertThatExceptionOfType(UnifiedConfigurationException.class)
        .isThrownBy(() -> validation.validate(resolved))
        .withMessageContaining("tenanta")
        .withMessageContaining("tenantb")
        .withMessageContaining("es|shared");
  }

  @Test
  void shouldRejectTwoTenantsClaimingTheSameLifecyclePolicy() {
    // given two tenants whose generic exporters manage the same lifecycle policy
    final Map<String, Camunda> resolved =
        tenants(
            "tenanta", camunda(exporter("esaudit", MERGED_CLASS, "es|a", "es|policy")),
            "tenantb", camunda(exporter("esaudit", MERGED_CLASS, "es|b", "es|policy")));

    // when / then the lifecycle-policy collision is rejected even though the index targets differ
    assertThatExceptionOfType(UnifiedConfigurationException.class)
        .isThrownBy(() -> validation.validate(resolved))
        .withMessageContaining("tenanta")
        .withMessageContaining("tenantb")
        .withMessageContaining("es|policy");
  }

  @Test
  void shouldPassWhenKeysDifferWithinADomain() {
    // given two tenants with distinct index keys
    final Map<String, Camunda> resolved =
        tenants(
            "tenanta", camunda(exporter("esaudit", MERGED_CLASS, "es|a", null)),
            "tenantb", camunda(exporter("esaudit", MERGED_CLASS, "es|b", null)));

    // when / then distinct keys isolate the tenants
    assertThatCode(() -> validation.validate(resolved)).doesNotThrowAnyException();
  }

  @Test
  void shouldNotCollideAcrossDomainsEvenWithEqualKeys() {
    // given tenant a claims an index target with key K, tenant b a lifecycle policy with key K
    final Map<String, Camunda> resolved =
        tenants(
            "tenanta", camunda(exporter("esaudit", MERGED_CLASS, "same-key", null)),
            "tenantb", camunda(exporter("esaudit", MERGED_CLASS, null, "same-key")));

    // when / then the domain participates in identity — equal keys in different domains never
    // collide
    assertThatCode(() -> validation.validate(resolved)).doesNotThrowAnyException();
  }

  @Test
  void shouldReportCollisionsInEveryDomainThatCollides() {
    // given two tenants sharing BOTH an index target and a lifecycle policy
    final Map<String, Camunda> resolved =
        tenants(
            "tenanta", camunda(exporter("esaudit", MERGED_CLASS, "es|idx", "es|pol")),
            "tenantb", camunda(exporter("esaudit", MERGED_CLASS, "es|idx", "es|pol")));

    // when / then both collisions are reported
    assertThatExceptionOfType(UnifiedConfigurationException.class)
        .isThrownBy(() -> validation.validate(resolved))
        .withMessageContaining("es|idx")
        .withMessageContaining("es|pol");
  }

  @Test
  void shouldSkipAutoconfiguredExporterIds() {
    // given both tenants carry an autoconfigured camundaexporter claiming the same resource — those
    // are isolated by the secondary-storage validations, not here
    final Map<String, Camunda> resolved =
        tenants(
            "tenanta", camunda(exporter("camundaexporter", MERGED_CLASS, "es|shared", null)),
            "tenantb", camunda(exporter("camundaexporter", MERGED_CLASS, "es|shared", null)));

    // when / then the autoconfigured id is skipped by id, so no collision is raised here
    assertThatCode(() -> validation.validate(resolved)).doesNotThrowAnyException();
  }

  @Test
  void shouldSkipExportersWhoseClassShipsNoMerger() {
    // given two tenants with an identical custom exporter whose class has no discoverable merger
    final Map<String, Camunda> resolved =
        tenants(
            "tenanta", camunda(exporter("custom", UNMERGED_CLASS, "es|shared", null)),
            "tenantb", camunda(exporter("custom", UNMERGED_CLASS, "es|shared", null)));

    // when / then best-effort: no merger means no claims, so it is silently skipped
    assertThatCode(() -> validation.validate(resolved)).doesNotThrowAnyException();
  }

  @Test
  void shouldSkipExporterWithNullClassName() {
    // given a tenant-private entry without a class name (nothing to introspect)
    final Map<String, Camunda> resolved =
        tenants(
            "tenanta", camunda(exporter("mystery", null, "es|shared", null)),
            "tenantb", camunda(exporter("esaudit", MERGED_CLASS, "es|other", null)));

    // when / then the class-less entry is skipped, no collision
    assertThatCode(() -> validation.validate(resolved)).doesNotThrowAnyException();
  }

  @Test
  void shouldNotFlagTwoExportersOfTheSameTenantClaimingOneResource() {
    // given one tenant with two exporters claiming the same resource, and a second tenant elsewhere
    final Camunda a =
        camunda(
            exporter("esone", MERGED_CLASS, "es|shared", null),
            exporter("estwo", MERGED_CLASS, "es|shared", null));
    final Map<String, Camunda> resolved =
        tenants(
            "tenanta", a, "tenantb", camunda(exporter("esaudit", MERGED_CLASS, "es|other", null)));

    // when / then the resource maps to a single tenant id and is not flagged
    assertThatCode(() -> validation.validate(resolved)).doesNotThrowAnyException();
  }

  @Test
  void shouldPassForSingleTenantMap() {
    // given a single-tenant deployment
    final Map<String, Camunda> resolved =
        tenants("default", camunda(exporter("esaudit", MERGED_CLASS, "es|shared", null)));

    // when / then uniqueness over one entry is a no-op
    assertThatCode(() -> validation.validate(resolved)).doesNotThrowAnyException();
  }

  @Test
  void shouldReportOneGroupedErrorWhenThreeTenantsShareOneResource() {
    // given three tenants all claiming the same index target
    final Map<String, Camunda> resolved =
        tenants(
            "tenanta", camunda(exporter("esaudit", MERGED_CLASS, "es|shared", null)),
            "tenantb", camunda(exporter("esaudit", MERGED_CLASS, "es|shared", null)),
            "tenantc", camunda(exporter("esaudit", MERGED_CLASS, "es|shared", null)));

    // when / then one grouped error names all three and mentions the resource exactly once
    assertThatExceptionOfType(UnifiedConfigurationException.class)
        .isThrownBy(() -> validation.validate(resolved))
        .withMessageContaining("tenanta")
        .withMessageContaining("tenantb")
        .withMessageContaining("tenantc")
        .satisfies(e -> assertThat(countOccurrences(e.getMessage(), "es|shared")).isEqualTo(1));
  }

  @Test
  void shouldRejectAMergerThatMutatesTheArgsItIsGiven() {
    // given a merger that writes into the args map it is handed — which is the resolved
    // configuration object graph itself
    final GenericExporterIsolationValidation mutating =
        new GenericExporterIsolationValidation(
            () ->
                List.of(
                    new ExporterConfigMerger() {
                      @Override
                      public boolean supports(final String c) {
                        return MERGED_CLASS.equals(c);
                      }

                      @Override
                      public Map<String, Object> merge(
                          final Map<String, Object> rootArgs,
                          final Map<String, Object> tenantArgs) {
                        return tenantArgs;
                      }

                      @Override
                      public Set<ExporterIsolationClaim> isolationClaims(
                          final Map<String, Object> args) {
                        args.put("indexKey", "hijacked");
                        return Set.of();
                      }
                    }));
    final IdentifiedExporter entry = exporter("esaudit", MERGED_CLASS, "es|a", null);
    final Map<String, Camunda> resolved =
        tenants(
            "tenanta",
            camunda(entry),
            "tenantb",
            camunda(exporter("esaudit", MERGED_CLASS, "es|b", null)));

    // when / then the write is refused and reported as a configuration error naming the entry,
    // and the tenant's resolved args are left exactly as configured
    assertThatExceptionOfType(UnifiedConfigurationException.class)
        .isThrownBy(() -> mutating.validate(resolved))
        .withMessageContaining("esaudit")
        .withMessageContaining("tenanta");
    assertThat(entry.exporter().getArgs()).containsEntry("indexKey", "es|a");
  }

  @Test
  void shouldNotBeAffectedByAMergerMutatingTheIdentityItReturned() {
    // given a merger that hands out a mutable identity map and later edits the one it already
    // returned — the identity is used as a grouping key, so an unfrozen map would move the entry
    // out from under its own hash and the collision would go unreported
    final GenericExporterIsolationValidation leaky =
        new GenericExporterIsolationValidation(() -> List.of(new IdentityMutatingMerger()));
    final Map<String, Camunda> resolved =
        tenants(
            "tenanta", camunda(exporter("esaudit", MERGED_CLASS, "es|shared", null)),
            "tenantb", camunda(exporter("esaudit", MERGED_CLASS, "es|shared", null)));

    // when / then the collision is still detected
    assertThatExceptionOfType(UnifiedConfigurationException.class)
        .isThrownBy(() -> leaky.validate(resolved))
        .withMessageContaining("tenanta")
        .withMessageContaining("tenantb");
  }

  @Test
  void shouldReportAMergerThatFailsWhileDeclaringItsClaims() {
    // given a merger whose isolationClaims throws
    final GenericExporterIsolationValidation failing =
        new GenericExporterIsolationValidation(
            () ->
                List.of(
                    new ExporterConfigMerger() {
                      @Override
                      public boolean supports(final String c) {
                        return MERGED_CLASS.equals(c);
                      }

                      @Override
                      public Map<String, Object> merge(
                          final Map<String, Object> rootArgs,
                          final Map<String, Object> tenantArgs) {
                        return tenantArgs;
                      }

                      @Override
                      public Set<ExporterIsolationClaim> isolationClaims(
                          final Map<String, Object> args) {
                        throw new IllegalStateException("intentional test claim failure");
                      }
                    }));
    final Map<String, Camunda> resolved =
        tenants(
            "tenanta", camunda(exporter("esaudit", MERGED_CLASS, "es|a", null)),
            "tenantb", camunda(exporter("esaudit", MERGED_CLASS, "es|b", null)));

    // when / then the failure surfaces as a configuration error naming the offending entry
    assertThatExceptionOfType(UnifiedConfigurationException.class)
        .isThrownBy(() -> failing.validate(resolved))
        .withMessageContaining("esaudit")
        .withMessageContaining("tenanta")
        .withCauseInstanceOf(IllegalStateException.class);
  }

  // --- helpers -----------------------------------------------------------------------------------

  /**
   * A fake merger that turns two optional args — {@code indexKey} and {@code lifecycleKey} — into
   * an index-write-target claim and/or a lifecycle-policy claim. This exercises the domain-agnostic
   * grouping without depending on the concrete exporter modules or their key formats.
   */
  private static ExporterConfigMerger fakeMerger(final String className) {
    return new ExporterConfigMerger() {
      @Override
      public boolean supports(final String c) {
        return className.equals(c);
      }

      @Override
      public Map<String, Object> merge(
          final Map<String, Object> rootArgs, final Map<String, Object> tenantArgs) {
        return tenantArgs;
      }

      @Override
      public Set<ExporterIsolationClaim> isolationClaims(final Map<String, Object> args) {
        final Set<ExporterIsolationClaim> claims = new LinkedHashSet<>();
        final Object indexKey = args.get("indexKey");
        if (indexKey instanceof final String k) {
          claims.add(
              new ExporterIsolationClaim(INDEX, Map.of("id", k), "index write target [" + k + "]"));
        }
        final Object lifecycleKey = args.get("lifecycleKey");
        if (lifecycleKey instanceof final String k) {
          claims.add(
              new ExporterIsolationClaim(
                  LIFECYCLE, Map.of("id", k), "lifecycle policy [" + k + "]"));
        }
        return claims;
      }
    };
  }

  private static IdentifiedExporter exporter(
      final String id,
      final @Nullable String className,
      final @Nullable String indexKey,
      final @Nullable String lifecycleKey) {
    final Exporter e = new Exporter();
    e.setClassName(className);
    final Map<String, Object> args = new HashMap<>();
    if (indexKey != null) {
      args.put("indexKey", indexKey);
    }
    if (lifecycleKey != null) {
      args.put("lifecycleKey", lifecycleKey);
    }
    e.setArgs(args);
    return new IdentifiedExporter(id, e);
  }

  private static Camunda camunda(final IdentifiedExporter... exporters) {
    final Camunda camunda = new Camunda();
    final Map<String, Exporter> map = new LinkedHashMap<>();
    for (final IdentifiedExporter e : exporters) {
      map.put(e.id(), e.exporter());
    }
    camunda.getData().setExporters(map);
    return camunda;
  }

  private static Map<String, Camunda> tenants(final Object... idThenCamunda) {
    final Map<String, Camunda> map = new LinkedHashMap<>();
    for (int i = 0; i < idThenCamunda.length; i += 2) {
      map.put((String) idThenCamunda[i], (Camunda) idThenCamunda[i + 1]);
    }
    return map;
  }

  private static int countOccurrences(final String haystack, final String needle) {
    int count = 0;
    int from = 0;
    while ((from = haystack.indexOf(needle, from)) >= 0) {
      count++;
      from += needle.length();
    }
    return count;
  }

  /** An {@link Exporter} paired with the id it should occupy in a tenant's exporter map. */
  private record IdentifiedExporter(String id, Exporter exporter) {}

  /**
   * Returns a fresh <em>mutable</em> identity per call and, from the second call on, edits the map
   * it returned previously — the worst case a defensive copy of the identity has to survive.
   */
  private static final class IdentityMutatingMerger implements ExporterConfigMerger {

    private final List<Map<String, Object>> handedOut = new ArrayList<>();

    @Override
    public boolean supports(final String className) {
      return MERGED_CLASS.equals(className);
    }

    @Override
    public Map<String, Object> merge(
        final Map<String, Object> rootArgs, final Map<String, Object> tenantArgs) {
      return tenantArgs;
    }

    @Override
    public Set<ExporterIsolationClaim> isolationClaims(final Map<String, Object> args) {
      handedOut.forEach(identity -> identity.put("id", "mutated-after-the-fact"));
      final Map<String, Object> identity = new HashMap<>(Map.of("id", args.get("indexKey")));
      handedOut.add(identity);
      return Set.of(new ExporterIsolationClaim(INDEX, identity, "index write target"));
    }
  }
}
