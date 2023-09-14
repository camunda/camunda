/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.state.deployment;

import io.camunda.zeebe.msgpack.property.ArrayProperty;
import io.camunda.zeebe.msgpack.value.LongValue;
import java.util.List;
import java.util.Optional;
import java.util.stream.StreamSupport;

public interface VersionInfo<T> {

  long getHighestVersion();

  void setHighestVersion(final long version);

  ArrayProperty<LongValue> getKnownVersionsProp();

  /**
   * Sets the highest version of a resource. This is the highest version we've ever known. If the
   * passed version is lower than the current known highest version, nothing is changed.
   *
   * @param version the version of the resource
   */
  default T setHighestVersionIfHigher(final long version) {
    if (version > getHighestVersion()) {
      setHighestVersion(version);
    }
    return (T) this;
  }

  default Long getLatestVersion() {
    final List<Long> knownVersions = getKnownVersions();
    if (knownVersions.isEmpty()) {
      return 0L;
    }
    return knownVersions.get(knownVersions.size() - 1);
  }

  default List<Long> getKnownVersions() {
    return StreamSupport.stream(getKnownVersionsProp().spliterator(), false)
        .map(LongValue::getValue)
        .sorted()
        .toList();
  }

  default Optional<Integer> findVersionBefore(final long version) {
    final var knownVersions = getKnownVersions();
    final var previousIndex = knownVersions.indexOf(version) - 1;

    if (previousIndex >= knownVersions.size() || previousIndex < 0) {
      return Optional.empty();
    }

    return Optional.of(knownVersions.get(previousIndex).intValue());
  }

  default void addKnownVersion(final long version) {
    if (!getKnownVersions().contains(version)) {
      getKnownVersionsProp().add().setValue(version);
      setHighestVersionIfHigher(version);
    }
  }

  default void removeKnownVersion(final long version) {
    final var iterator = getKnownVersionsProp().iterator();
    while (iterator.hasNext()) {
      if (iterator.next().getValue() == version) {
        iterator.remove();
        break;
      }
    }
  }
}
