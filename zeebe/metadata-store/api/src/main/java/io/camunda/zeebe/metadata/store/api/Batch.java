/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.metadata.store.api;

import io.camunda.zeebe.metadata.store.api.ClusterMetadataStoreException.InvalidArgumentException;
import io.camunda.zeebe.metadata.store.api.Write.Check;
import io.camunda.zeebe.metadata.store.api.Write.CreateNamespace;
import io.camunda.zeebe.metadata.store.api.Write.Delete;
import io.camunda.zeebe.metadata.store.api.Write.DeletePrefix;
import io.camunda.zeebe.metadata.store.api.Write.Put;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Writes applied together, all or nothing, and checked in their order, so a {@link CreateNamespace}
 * can be followed by writes into that namespace. A batch that breaks a rule that needs no store
 * state cannot be built:
 *
 * <ul>
 *   <li>two writes do not address the same key, a {@link Check} included;
 *   <li>a {@link Put}, {@link Delete} or {@link Check} does not address a key at or under the
 *       prefix of a {@link DeletePrefix};
 *   <li>a namespace is created at most once.
 * </ul>
 *
 * @param writes the writes, in order
 */
public record Batch(List<Write> writes) {

  /**
   * @throws InvalidArgumentException if the writes break one of the rules
   */
  public Batch {
    writes = List.copyOf(writes);
    validate(writes);
  }

  /**
   * @throws InvalidArgumentException if the writes break one of the rules
   */
  public static Batch of(final Write... writes) {
    return new Batch(List.of(writes));
  }

  private static void validate(final List<Write> writes) {
    final var keys = writes.stream().flatMap(Batch::keyOf).toList();
    requireUnique(keys, "Expected each key at most once in a batch, but got '%s' again");
    requireUnique(
        writes.stream()
            .filter(CreateNamespace.class::isInstance)
            .map(CreateNamespace.class::cast)
            .map(CreateNamespace::namespace)
            .toList(),
        "Expected a batch to create namespace '%s' at most once, but it creates it again");

    writes.stream()
        .filter(DeletePrefix.class::isInstance)
        .map(DeletePrefix.class::cast)
        .map(DeletePrefix::prefix)
        .flatMap(
            prefix ->
                keys.stream()
                    .filter(key -> !key.equals(prefix) && key.isAtOrUnder(prefix))
                    .map(
                        key ->
                            new InvalidArgumentException(
                                ("Expected no write in a batch at or under the deleted prefix"
                                        + " '%s', but got key '%s'")
                                    .formatted(prefix, key))))
        .findFirst()
        .ifPresent(
            e -> {
              throw e;
            });
  }

  /** The key a write addresses, if any; a {@link DeletePrefix} addresses its prefix. */
  private static Stream<StoreKey> keyOf(final Write write) {
    return switch (write) {
      case final Put put -> Stream.of(put.key());
      case final Delete delete -> Stream.of(delete.key());
      case final Check check -> Stream.of(check.key());
      case final DeletePrefix deletePrefix -> Stream.of(deletePrefix.prefix());
      case final CreateNamespace ignored -> Stream.empty();
    };
  }

  /** Throws for the first item, in order, that appears more than once. */
  private static <T> void requireUnique(final List<T> items, final String message) {
    items.stream()
        .collect(
            Collectors.groupingBy(Function.identity(), LinkedHashMap::new, Collectors.counting()))
        .entrySet()
        .stream()
        .filter(entry -> entry.getValue() > 1)
        .findFirst()
        .ifPresent(
            entry -> {
              throw new InvalidArgumentException(message.formatted(entry.getKey()));
            });
  }
}
