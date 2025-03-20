/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.webapps.schema.descriptors.backup;

import io.camunda.webapps.schema.descriptors.IndexTemplateDescriptor;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

public record BackupPriorities(
    List<Prio1Backup> prio1,
    List<Prio2Backup> prio2,
    List<Prio3Backup> prio3,
    List<Prio4Backup> prio4,
    List<Prio5Backup> prio5) {

  public Stream<BackupPriority> allPriorities() {
    return Stream.of(prio1(), prio2(), prio3(), prio4(), prio5()).flatMap(List::stream);
  }

  public Stream<SnapshotIndexCollection> indicesSplitBySnapshot() {
    return Stream.of(
        fullQualifiedName(prio1()),
        fullQualifiedName(prio2()),
        // dated indices
        fullQualifiedNameWithMatcher(prio2()),
        fullQualifiedName(prio3()),
        fullQualifiedName(prio4()),
        // dated indices
        fullQualifiedNameWithMatcher(prio4()),
        fullQualifiedName(prio5()));
  }

  private static <A extends BackupPriority> SnapshotIndexCollection fullQualifiedName(
      final Collection<A> backups) {
    return SnapshotIndexCollection.of(backups);
  }

  private static <A extends BackupPriority> SnapshotIndexCollection fullQualifiedNameWithMatcher(
      final Collection<A> backups) {
    final var indices =
        backups.stream()
            .filter(IndexTemplateDescriptor.class::isInstance)
            .map(IndexTemplateDescriptor.class::cast)
            .map(IndexTemplateDescriptor::getFullQualifiedName)
            .flatMap(name -> Stream.of(name + "*", "-" + name))
            .toList();
    return new SnapshotIndexCollection(Collections.emptyList(), indices);
  }
}
