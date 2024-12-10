/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.webapps.schema.descriptors.backup;

import java.util.List;
import java.util.stream.Stream;

public record BackupPriorities(
    List<Prio1Backup> prio1,
    List<Prio2Backup> prio2,
    List<Prio3Backup> prio3,
    List<Prio4Backup> prio4,
    List<Prio5Backup> prio5,
    List<Prio6Backup> prio6) {

  public Stream<BackupPriority> allPriorities() {
    return Stream.of(prio1(), prio2(), prio3(), prio4(), prio5(), prio6()).flatMap(List::stream);
  }
}
