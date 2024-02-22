/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.test.util.asserts;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Stream;
import org.assertj.core.api.AbstractPathAssert;
import org.assertj.core.api.InstanceOfAssertFactory;

/**
 * An extension of {@link AbstractPathAssert} to add some functionality specifically for
 * directories.
 *
 * <p>You can easily extend an existing {@link AbstractPathAssert} by using the factory method, as:
 *
 * <p>{@code
 * assertThat(myPath).asInstanceOf(DirectoryAssert.factory().isDirectoryContainingExactly(...) }
 */
@SuppressWarnings({"UnusedReturnValue", "unused", "java:S5803"})
public final class DirectoryAssert extends AbstractPathAssert<DirectoryAssert> {

  public DirectoryAssert(final Path actual) {
    super(actual, DirectoryAssert.class);
  }

  public static DirectoryAssert assertThat(final Path actual) {
    return new DirectoryAssert(actual);
  }

  /**
   * A convenience method to extend existing {@link AbstractPathAssert} by using {@link
   * #asInstanceOf(InstanceOfAssertFactory)}.
   *
   * @return a factory for this assertion class
   */
  public static InstanceOfAssertFactory<Path, DirectoryAssert> factory() {
    return new InstanceOfAssertFactory<>(Path.class, DirectoryAssert::assertThat);
  }

  /**
   * Asserts that {@link #actual} contains exactly only the paths in the given collection.
   *
   * <p>NOTE: this is not traversing {@link #actual} recursively, and will only look at the direct
   * descendants.
   *
   * <p>NOTE: while you can use this to assert the directory is empty, it's better to use {@link
   * #isEmptyDirectory()}.
   *
   * @param collection the paths to check for
   * @throws AssertionError if {@link #actual} is not a directory
   * @throws AssertionError if {@link #actual} contains paths not specified in {@code collection}
   * @throws AssertionError if there is at least one path from {@code collection} not contained in
   *     {@link #actual}
   * @return this assert for chaining
   */
  public DirectoryAssert isDirectoryContainingExactly(final Collection<? extends Path> collection) {
    paths.assertIsDirectory(info, actual);

    final Set<Path> containedPaths = new HashSet<>();
    final Set<Path> expectedPaths = new HashSet<>(collection);

    try (final Stream<Path> files = Files.list(actual)) {
      files.forEach(containedPaths::add);
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }

    final Set<Path> missingPaths = new HashSet<>(expectedPaths);
    final Set<Path> extraPaths = new HashSet<>(containedPaths);
    missingPaths.removeAll(containedPaths);
    extraPaths.removeAll(expectedPaths);

    if (!missingPaths.isEmpty() || !extraPaths.isEmpty()) {
      throw failure(
          "%nPath %s should contain exactly the following %n%s%nBut found %n%s%nMissing paths: %n%s%nUnexpected paths: %n%s",
          actual, expectedPaths, containedPaths, missingPaths, extraPaths);
    }

    return myself;
  }

  /**
   * @see #isDirectoryContainingExactly(Collection)
   */
  public DirectoryAssert isDirectoryContainingExactly(final Path... paths) {
    return isDirectoryContainingExactly(Arrays.asList(paths));
  }

  /**
   * Asserts that {@link #actual} contains all of the paths in the given collection, but may contain
   * extra, unspecified paths. Use {@link #isDirectoryContainingExactly(Collection)} if you need an
   * exact comparison.
   *
   * <p>NOTE: this is not traversing {@link #actual} recursively, and will only look at the direct
   * descendants.
   *
   * @param collection the paths to check for
   * @throws AssertionError if {@link #actual} is not a directory
   * @throws AssertionError if there is at least one path from {@code collection} not contained in
   *     {@link #actual}
   * @return this assert for chaining
   */
  public DirectoryAssert isDirectoryContainingAllOf(final Collection<? extends Path> collection) {
    paths.assertIsDirectory(info, actual);

    final Set<Path> containedPaths = new HashSet<>();
    final Set<Path> expectedPaths = new HashSet<>(collection);

    try (final Stream<Path> files = Files.list(actual)) {
      files.forEach(containedPaths::add);
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }

    final Set<Path> missingPaths = new HashSet<>(expectedPaths);
    missingPaths.removeAll(containedPaths);

    if (!missingPaths.isEmpty()) {
      throw failure(
          "%nPath %s should contain exactly the following %n%s%nBut found %n%s%nMissing paths: %n%s",
          actual, expectedPaths, containedPaths, missingPaths);
    }

    return myself;
  }

  /**
   * @see #isDirectoryContainingAllOf(Collection)
   */
  public DirectoryAssert isDirectoryContainingAllOf(final Path... paths) {
    return isDirectoryContainingAllOf(Arrays.asList(paths));
  }
}
