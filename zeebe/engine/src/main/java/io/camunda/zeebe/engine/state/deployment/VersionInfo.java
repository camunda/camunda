/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.deployment;

import io.camunda.zeebe.db.DbValue;
import io.camunda.zeebe.msgpack.pojo.MsgpackBufferValue;
import io.camunda.zeebe.msgpack.pojo.MsgpackPojo;
import io.camunda.zeebe.msgpack.pojo.MsgpackProperty;
import io.camunda.zeebe.msgpack.pojo.MsgpackUnknownProperties;
import io.camunda.zeebe.msgpack.pojo.UnknownProperties;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Plain data; the msgpack codec is generated, see {@link MsgpackPojo}. The field names and their
 * order are the stored format.
 */
@MsgpackPojo
public final class VersionInfo extends MsgpackBufferValue<VersionInfo> implements DbValue {

  // The stored key is named nextValue. This is not a great name and doesn't describe what it is.
  // However, changing this is not backwards compatible. Changing the variable name is the best we
  // can do to hide this name.
  @MsgpackProperty("nextValue")
  long highestVersion = -1L;

  final List<Long> knownVersions = new ArrayList<>();

  @MsgpackUnknownProperties final UnknownProperties unknownProperties = new UnknownProperties();

  public VersionInfo() {}

  public VersionInfo(final VersionInfo other) {
    this();
    final var copy = BufferUtil.createCopy(other);
    wrap(copy, 0, copy.capacity());
  }

  /**
   * Gets the highest version of a resource. This is the highest version we've ever known. There is
   * no guarantee that a resource with this version still exists in the state. It could've been
   * deleted. We need to track this version so we don't ever reuse version numbers after a resource
   * has been deleted.
   *
   * @return the highest version we've ever known for this resource
   */
  public long getHighestVersion() {
    return highestVersion;
  }

  public VersionInfo setHighestVersionIfHigher(final long version) {
    if (version > getHighestVersion()) {
      highestVersion = version;
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
    return knownVersions.stream().sorted().toList();
  }

  public Optional<Integer> findVersionBefore(final long version) {
    final var knownVersions = getKnownVersions();
    final var previousIndex = knownVersions.indexOf(version) - 1;

    if (previousIndex >= knownVersions.size() || previousIndex < 0) {
      return Optional.empty();
    }

    return Optional.of(knownVersions.get(previousIndex).intValue());
  }

  public void addKnownVersion(final long version) {
    if (!knownVersions.contains(version)) {
      knownVersions.add(version);
      setHighestVersionIfHigher(version);
    }
  }

  public void removeKnownVersion(final long version) {
    knownVersions.remove(Long.valueOf(version));
  }
}
