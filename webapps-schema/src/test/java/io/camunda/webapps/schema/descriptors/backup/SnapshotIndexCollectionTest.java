/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.webapps.schema.descriptors.backup;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

public class SnapshotIndexCollectionTest {

  @Test
  public void shouldSplitBackupsOnRequired() {
    final var collection =
        SnapshotIndexCollection.of(
            List.of(
                new Backup("required-1", true),
                new Backup("skippable-1", false),
                new Backup("required-2", true)));

    assertThat(collection.requiredIndices()).containsExactly("required-1", "required-2");
    assertThat(collection.skippableIndices()).containsExactly("skippable-1");
    assertThat(collection.allIndices()).containsExactly("required-1", "required-2", "skippable-1");
  }

  private record Backup(String name, boolean required) implements BackupPriority {
    @Override
    public String getFullQualifiedName() {
      return name;
    }
  }
}
