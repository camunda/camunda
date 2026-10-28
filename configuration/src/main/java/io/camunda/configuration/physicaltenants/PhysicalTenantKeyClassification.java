/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration.physicaltenants;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Decides how a changed property of a physical tenant takes effect at runtime: by a restart of the
 * tenant's module, or not at all, in which case the change is rejected. A property that has no
 * classification is rejected too, so a property added later never changes silently.
 *
 * <p>The classification of every overridable property is listed in {@code key-classification.txt}
 * next to this class. A changed name is classified by the longest listed property it equals or is
 * nested under, so {@code data.exporters.foo.args.bar} is classified as {@code data.exporters}.
 * Names must be in the canonical kebab-case form.
 *
 * <p>Two rules look at more than single properties: the set of exporter ids of a tenant cannot
 * change, and its display name ({@code name}) is applied by a restart.
 */
@NullMarked
public final class PhysicalTenantKeyClassification implements PhysicalTenantConfigValidator {

  static final String RESOURCE = "key-classification.txt";
  private static final String NAME = "name";
  private static final String EXPORTERS = "data.exporters.";

  private final Map<String, Classification> classifications;

  PhysicalTenantKeyClassification(final Map<String, Classification> classifications) {
    this.classifications = Collections.unmodifiableMap(new TreeMap<>(classifications));
  }

  public static PhysicalTenantKeyClassification load() {
    final var stream = PhysicalTenantKeyClassification.class.getResourceAsStream(RESOURCE);
    if (stream == null) {
      throw new IllegalStateException("Missing classpath resource " + RESOURCE);
    }
    try (final var reader =
        new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
      return new PhysicalTenantKeyClassification(
          reader
              .lines()
              .map(String::strip)
              .filter(line -> !line.isEmpty() && !line.startsWith("#"))
              .map(line -> line.split("\\s+"))
              .collect(
                  Collectors.toMap(
                      fields -> fields[0],
                      fields ->
                          new Classification(
                              Kind.valueOf(fields[1]), fields.length > 2 ? fields[2] : null))));
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /** The properties that have a classification. */
  public Set<String> classifiedProperties() {
    return classifications.keySet();
  }

  /** The classification of a property name, or empty if it has none. */
  public Optional<Classification> classify(final String name) {
    if (NAME.equals(name)) {
      return Optional.of(new Classification(Kind.RESTART, null));
    }
    var candidate = name;
    while (!candidate.isEmpty()) {
      final var classification = classifications.get(candidate);
      if (classification != null) {
        return Optional.of(classification);
      }
      candidate = parentOf(candidate);
    }
    return Optional.empty();
  }

  @Override
  public List<String> validate(final PhysicalTenantConfigChange change) {
    if (change.current().isEmpty()) {
      return List.of();
    }
    final var violations = new ArrayList<String>();
    final var exportersBefore = exporterIds(change.current().get());
    final var exportersAfter = exporterIds(change.proposed());
    if (!exportersBefore.equals(exportersAfter)) {
      violations.add(
          "The exporters of a physical tenant cannot change at runtime (%s -> %s)"
              .formatted(exportersBefore, exportersAfter));
    }
    for (final var name : change.changedProperties()) {
      final var classification = classify(name);
      if (classification.isEmpty()) {
        violations.add("'%s' has no runtime classification and cannot change".formatted(name));
      } else if (classification.get().kind() == Kind.REJECT) {
        violations.add(
            "'%s' cannot change at runtime (%s)".formatted(name, classification.get().reason()));
      }
    }
    return violations;
  }

  private static Set<String> exporterIds(final Map<String, String> properties) {
    return properties.keySet().stream()
        .filter(name -> name.startsWith(EXPORTERS))
        .map(name -> name.substring(EXPORTERS.length()).split("[.\\[]", 2)[0])
        .collect(Collectors.toCollection(TreeSet::new));
  }

  private static String parentOf(final String name) {
    final var cut = Math.max(name.lastIndexOf('.'), name.lastIndexOf('['));
    return cut < 0 ? "" : name.substring(0, cut);
  }

  public enum Kind {
    /** Applied by a restart of the tenant's module. */
    RESTART,
    /** Rejected. */
    REJECT
  }

  public record Classification(Kind kind, @Nullable String reason) {
    public Classification {
      Objects.requireNonNull(kind);
    }
  }
}
