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
package io.camunda.zeebe.exporter.api;

import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.NullMarked;

/**
 * Opt-in, per-exporter-class deep merge of exporter {@code args} maps, used when resolving
 * per-physical-tenant configuration: a tenant that partially overrides a root-declared exporter's
 * {@code args} gets the root args and its own args deep-merged (tenant wins where both set a key) —
 * but only if the exporter's class ships a merger, because only code that owns the exporter's
 * configuration class can merge its args safely (property keys must be normalized against the
 * config class while the content of {@code Map}-typed fields is user data that must not be
 * rewritten).
 *
 * <p>Implementations are discovered via {@link java.util.ServiceLoader} at configuration-resolution
 * time. An exporter class without a discoverable merger keeps whole-map-replace semantics: the
 * tenant's args are taken exactly as declared. Two discovered mergers claiming the same class name
 * fail startup.
 *
 * <p>Implementations should be stateless; {@link #merge(Map, Map)} must not mutate its inputs.
 */
@NullMarked
public interface ExporterConfigMerger {

  /**
   * Whether this merger owns the configuration model of the given exporter class. Matched against
   * the fully qualified {@code className} of the root-declared exporter entry.
   *
   * @param className the fully qualified class name of the exporter (never {@code null})
   * @return true if this merger can merge args for the given exporter class
   */
  boolean supports(String className);

  /**
   * Deep-merges a tenant's partial {@code args} over the root-declared {@code args} of the same
   * exporter entry.
   *
   * @param rootArgs the root entry's args (never {@code null}, possibly empty; must not be mutated)
   * @param tenantArgs the tenant's overriding args (never {@code null}, possibly empty; must not be
   *     mutated)
   * @return the merged args map
   */
  Map<String, Object> merge(Map<String, Object> rootArgs, Map<String, Object> tenantArgs);

  /**
   * The resources an exporter with the given resolved {@code args} would exclusively occupy, so the
   * platform can reject two configurations that would collide on the same resource (e.g. two
   * writing into the same indices, or managing the same cluster-global lifecycle policy). An
   * exporter typically emits one claim per kind of resource it occupies.
   *
   * <p>Returns an empty set by default: an exporter that declares no claims is simply not
   * isolation-checked — the same "no merger, no special handling" fallback {@link #merge} has for
   * whole-map replace. Derive the claims purely from {@code args} (applying the exporter's own
   * defaults for keys the map omits) and, like {@link #merge}, do not mutate the input.
   *
   * @param args the exporter's resolved args (never {@code null}, possibly empty; must not be
   *     mutated)
   * @return the resources this exporter would occupy, or an empty set if it declares none
   */
  default Set<ExporterIsolationClaim> isolationClaims(final Map<String, Object> args) {
    return Set.of();
  }

  /**
   * A resource an exporter would exclusively occupy. Two claims collide when their {@code domain}
   * and {@code identity} are equal.
   *
   * @param domain groups comparable resources so unrelated ones never collide (e.g. {@code
   *     "index-write-target"}, {@code "lifecycle-policy"})
   * @param identity the already-normalized fields that identify the resource within its {@code
   *     domain} — build it so that any two configurations occupying the same resource produce an
   *     equal map (normalize away incidental differences such as url casing or ordering)
   * @param description a human-readable rendering shown when a collision is reported; not part of
   *     the collision identity
   */
  record ExporterIsolationClaim(String domain, Map<String, Object> identity, String description) {}
}
