/*
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.camunda.client.spring.properties;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.env.Environment;

/**
 * Resolves the generic multi-client configuration under {@code camunda.clients.<name>.*} into a
 * {@link MultiCamundaClientProperties} holding one fully-resolved {@link CamundaClientProperties}
 * per named client.
 *
 * <p>Each entry is a <b>sparse overlay</b> onto the shared {@code camunda.client.*} base: a fresh
 * {@link CamundaClientProperties} is seeded by binding {@code camunda.client.*} onto it, then the
 * entry's own {@code camunda.clients.<name>.*} keys are bound over the top, so unset per-client
 * properties fall back to the global value. This reuses the authoritative single-client {@link
 * CamundaClientProperties} as the entry type, so multi-client entries automatically track any new
 * single-client property.
 *
 * <p>The binding is performed manually via {@link Binder} rather than through
 * {@code @ConfigurationProperties} precisely to achieve that overlay: standard map binding ({@code
 * Map<String, CamundaClientProperties>}) resolves each entry against only its own subtree, so
 * entries would start from class defaults and would <em>not</em> inherit the global {@code
 * camunda.client.*} base. Seeding each instance with the base and then binding the per-client keys
 * over it ({@link Bindable#ofInstance}) is the standard Spring Boot way to express this.
 *
 * <p>The map key is a free-form client name (not a physical tenant id). Physical-tenant targeting
 * is optional per entry via {@code physical-tenant-id}; when set it must be lowercase alphanumeric
 * and at most {@value #MAX_PHYSICAL_TENANT_ID_LENGTH} characters. The rule is mirrored from the
 * server-side {@code io.camunda.configuration.physicaltenants.PhysicalTenantResolver}; keep the two
 * in sync.
 *
 * <p>Exactly one entry may be marked with {@code camunda.clients.<name>.primary=true} to designate
 * the default client; if only one client is configured it is the primary implicitly.
 *
 * <p>All configured clients must share a single authentication <em>type</em> ({@code auth.method});
 * per-client credentials/identities may differ, but mixing e.g. {@code basic} and {@code oidc}
 * across clients fails fast. This mirrors the parent physical-tenant design.
 */
public final class MultiCamundaClientPropertiesResolver {

  static final int MAX_PHYSICAL_TENANT_ID_LENGTH = 64;
  // logical name of the sole client for a single-client application; mirrors
  // CamundaClientCreatedEvent.DEFAULT_CLIENT_NAME and the REST API's always-present default tenant
  private static final String DEFAULT_CLIENT_NAME = "default";
  private static final String CLIENT_PREFIX = "camunda.client";
  private static final String CLIENTS_PREFIX = "camunda.clients";
  // Mirrors PhysicalTenantResolver.VALID_TENANT_ID — keep in sync.
  private static final Pattern VALID_PHYSICAL_TENANT_ID = Pattern.compile("[a-z0-9]+");

  private MultiCamundaClientPropertiesResolver() {}

  /**
   * Resolves every {@code camunda.clients.<name>.*} entry against the {@code camunda.client.*}
   * base.
   */
  public static MultiCamundaClientProperties resolve(final Environment environment) {
    final Binder binder = Binder.get(environment);
    Set<String> names =
        binder
            .bind(CLIENTS_PREFIX, Bindable.mapOf(String.class, Object.class))
            .map(Map::keySet)
            .orElseGet(Collections::emptySet);

    if (names.isEmpty()) {
      // No explicit camunda.clients.<name>.* entries: this is a single-client (or unconfigured)
      // application. Project it onto exactly one client named 'default', seeded from the
      // camunda.client.* base below. Discovery and seeding both go through the Binder, so relaxed
      // and environment-variable configuration (e.g. CAMUNDA_CLIENT_REST_ADDRESS) is handled too.
      names = Set.of(DEFAULT_CLIENT_NAME);
    }

    final Map<String, CamundaClientProperties> resolved = new LinkedHashMap<>();
    for (final String name : names) {
      final CamundaClientProperties properties = new CamundaClientProperties();
      // Seed with the shared base, then overlay the per-client keys (per-client wins, else global).
      binder.bind(CLIENT_PREFIX, Bindable.ofInstance(properties));
      binder.bind(CLIENTS_PREFIX + "." + name, Bindable.ofInstance(properties));
      normalizePhysicalTenantId(properties);
      validatePhysicalTenantId(name, properties.getPhysicalTenantId());
      resolved.put(name, properties);
    }
    validateSingleAuthType(resolved);
    return new MultiCamundaClientProperties(resolved, resolvePrimaryClientName(binder, names));
  }

  /**
   * Validates that all configured clients share a single authentication type ({@code auth.method}).
   * Per-client credentials/identities may differ, but the type must be uniform.
   *
   * @throws IllegalArgumentException if clients declare more than one distinct {@code auth.method}
   */
  private static void validateSingleAuthType(final Map<String, CamundaClientProperties> clients) {
    final Map<String, CamundaClientAuthProperties.AuthMethod> methodByClient =
        new LinkedHashMap<>();
    clients.forEach(
        (name, properties) ->
            methodByClient.put(name, normalizeAuthMethod(properties.getAuth().getMethod())));
    final long distinctMethods = methodByClient.values().stream().distinct().count();
    if (distinctMethods > 1) {
      throw new IllegalArgumentException(
          String.format(
              "All 'camunda.clients.*' must use the same authentication method (auth.method); found "
                  + "mixed methods %s. Per-client credentials may differ, but the auth type must be "
                  + "uniform.",
              methodByClient));
    }
  }

  /**
   * Normalizes an unset {@code auth.method} to {@link CamundaClientAuthProperties.AuthMethod#none}:
   * both a {@code null} method and an explicit {@code none} resolve to a {@code
   * NoopCredentialsProvider}, so they must count as the same auth type.
   */
  private static CamundaClientAuthProperties.AuthMethod normalizeAuthMethod(
      final CamundaClientAuthProperties.AuthMethod method) {
    return method == null ? CamundaClientAuthProperties.AuthMethod.none : method;
  }

  /**
   * Resolves which client is primary: the single entry flagged {@code
   * camunda.clients.<name>.primary=true}, or — when none is flagged — the sole configured client.
   *
   * @throws IllegalArgumentException if more than one entry is flagged primary
   */
  private static String resolvePrimaryClientName(final Binder binder, final Set<String> names) {
    final List<String> flagged = new ArrayList<>();
    for (final String name : names) {
      final boolean primary =
          binder
              .bind(CLIENTS_PREFIX + "." + name + ".primary", Bindable.of(Boolean.class))
              .orElse(false);
      if (primary) {
        flagged.add(name);
      }
    }
    if (flagged.size() > 1) {
      throw new IllegalArgumentException(
          String.format(
              "Multiple clients are marked primary (%s); at most one 'camunda.clients.<name>."
                  + "primary=true' is allowed.",
              flagged));
    }
    if (flagged.size() == 1) {
      return flagged.get(0);
    }
    return names.size() == 1 ? names.iterator().next() : null;
  }

  /**
   * Normalizes a blank {@code physical-tenant-id} to {@code null} so that "blank means disabled"
   * holds in practice: the Java client enables the physical-tenant gRPC interceptor whenever the id
   * is non-null, so an empty string would otherwise send a {@code Camunda-Physical-Tenant} header
   * with an empty value. A non-blank value is stored trimmed, matching the client's own handling.
   */
  private static void normalizePhysicalTenantId(final CamundaClientProperties properties) {
    final String tenantId = properties.getPhysicalTenantId();
    if (tenantId == null) {
      return;
    }
    final String trimmed = tenantId.trim();
    properties.setPhysicalTenantId(trimmed.isEmpty() ? null : trimmed);
  }

  private static void validatePhysicalTenantId(final String clientName, final String tenantId) {
    // physical-tenant-id is optional; a null value (see normalizePhysicalTenantId) means "no
    // physical-tenant targeting".
    if (tenantId == null) {
      return;
    }
    if (tenantId.length() > MAX_PHYSICAL_TENANT_ID_LENGTH
        || !VALID_PHYSICAL_TENANT_ID.matcher(tenantId).matches()) {
      throw new IllegalArgumentException(
          String.format(
              "Invalid physical-tenant-id '%s' for 'camunda.clients.%s'. It must be lowercase "
                  + "alphanumeric (matching %s) and at most %d characters.",
              tenantId,
              clientName,
              VALID_PHYSICAL_TENANT_ID.pattern(),
              MAX_PHYSICAL_TENANT_ID_LENGTH));
    }
  }
}
