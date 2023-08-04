/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.state.deployment;

import io.camunda.zeebe.db.DbValue;
import io.camunda.zeebe.msgpack.UnpackedObject;
import io.camunda.zeebe.msgpack.property.ArrayProperty;
import io.camunda.zeebe.msgpack.property.LongProperty;
import io.camunda.zeebe.msgpack.value.LongValue;
import java.util.List;
import java.util.stream.StreamSupport;

public final class ProcessVersionInfo extends UnpackedObject implements DbValue {
  // The property key is named nextValue. This is not a great name and doesn't describe what it is.
  // However, changing this is not backwards compatible. Changing the variable name is the best we
  // can do to hide this name.
  private final LongProperty highestVersionProp = new LongProperty("nextValue", -1L);
  private final ArrayProperty<LongValue> knownVersions =
      new ArrayProperty<>("knownVersions", new LongValue());

  public ProcessVersionInfo() {
    declareProperty(highestVersionProp).declareProperty(knownVersions);
  }

  /**
   * Gets the highest version of a process. This is the highest version we've ever known. There is
   * no guarantee that a process with this version still exists in the state. It could've been
   * deleted. We need to track this version so we don't ever reuse version numbers after a process
   * has been deleted.
   *
   * @return the highest version we've ever known for this process
   */
  public long getHighestVersion() {
    return highestVersionProp.getValue();
  }

  /**
   * Sets the highest version of a process. This is the highest version we've ever known.
   *
   * @param version the version of the process
   */
  public ProcessVersionInfo setHighestVersion(final long version) {
    if (version > highestVersionProp.getValue()) {
      highestVersionProp.setValue(version);
    }
    return this;
  }

  public Long getLatestVersion() {
    final List<Long> knownVersions = getKnownVersions();
    if (knownVersions.isEmpty()) {
      return 0L;
    }
    return knownVersions.get(knownVersions.size() - 1);
  }

  public List<Long> getKnownVersions() {
    return StreamSupport.stream(knownVersions.spliterator(), false)
        .map(LongValue::getValue)
        .sorted()
        .toList();
  }

  public void addKnownVersion(final long version) {
    knownVersions.add().setValue(version);
    setHighestVersion(version);
  }

  public void removeKnownVersion(final long version) {
    final var iterator = knownVersions.iterator();
    while (iterator.hasNext()) {
      if (iterator.next().getValue() == version) {
        iterator.remove();
        break;
      }
    }
  }
}
