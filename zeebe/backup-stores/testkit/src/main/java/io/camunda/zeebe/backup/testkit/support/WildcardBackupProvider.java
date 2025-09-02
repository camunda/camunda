/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.backup.testkit.support;

import io.camunda.zeebe.backup.api.BackupIdentifierWildcard;
import io.camunda.zeebe.backup.api.BackupIdentifierWildcard.CheckpointPattern;
import io.camunda.zeebe.backup.common.BackupIdentifierImpl;
import io.camunda.zeebe.backup.common.BackupIdentifierWildcardImpl;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.ArgumentsProvider;

public class WildcardBackupProvider implements ArgumentsProvider {

  @Override
  public Stream<? extends Arguments> provideArguments(final ExtensionContext context) {
    return Stream.of(
        Arguments.of(
            Named.of(
                "Backups of arbitrary nodes",
                new WildcardTestParameter(
                    new BackupIdentifierWildcardImpl(
                        Optional.empty(), Optional.of(1), CheckpointPattern.of(1L)),
                    List.of(
                        new BackupIdentifierImpl(1, 1, 1),
                        new BackupIdentifierImpl(2, 1, 1),
                        new BackupIdentifierImpl(3, 1, 1)),
                    List.of(
                        new BackupIdentifierImpl(1, 2, 1), new BackupIdentifierImpl(2, 1, 2))))),
        Arguments.of(
            Named.of(
                "Backups of arbitrary partitions",
                new WildcardTestParameter(
                    new BackupIdentifierWildcardImpl(
                        Optional.of(1), Optional.empty(), CheckpointPattern.of(1L)),
                    List.of(
                        new BackupIdentifierImpl(1, 1, 1),
                        new BackupIdentifierImpl(1, 2, 1),
                        new BackupIdentifierImpl(1, 3, 1)),
                    List.of(
                        new BackupIdentifierImpl(2, 1, 1), new BackupIdentifierImpl(1, 1, 3))))),
        Arguments.of(
            Named.of(
                "Backups of arbitrary checkpoints",
                new WildcardTestParameter(
                    new BackupIdentifierWildcardImpl(
                        Optional.of(1), Optional.of(1), CheckpointPattern.any()),
                    List.of(
                        new BackupIdentifierImpl(1, 1, 1),
                        new BackupIdentifierImpl(1, 1, 2),
                        new BackupIdentifierImpl(1, 1, 3)),
                    List.of(
                        new BackupIdentifierImpl(1, 2, 1), new BackupIdentifierImpl(2, 1, 3))))),
        Arguments.of(
            Named.of(
                "Backups matching checkpoint prefix",
                new WildcardTestParameter(
                    new BackupIdentifierWildcardImpl(
                        Optional.of(1), Optional.of(1), CheckpointPattern.of("10*")),
                    List.of(
                        new BackupIdentifierImpl(1, 1, 10),
                        new BackupIdentifierImpl(1, 1, 100),
                        new BackupIdentifierImpl(1, 1, 101)),
                    List.of(
                        new BackupIdentifierImpl(1, 1, 1),
                        new BackupIdentifierImpl(1, 1, 20),
                        new BackupIdentifierImpl(2, 1, 10),
                        new BackupIdentifierImpl(1, 2, 10))))),
        Arguments.of(
            Named.of(
                "Backups of arbitrary partitions and checkpoints",
                new WildcardTestParameter(
                    new BackupIdentifierWildcardImpl(
                        Optional.of(1), Optional.empty(), CheckpointPattern.any()),
                    List.of(
                        new BackupIdentifierImpl(1, 1, 3),
                        new BackupIdentifierImpl(1, 2, 2),
                        new BackupIdentifierImpl(1, 3, 1)),
                    List.of(
                        new BackupIdentifierImpl(2, 2, 1), new BackupIdentifierImpl(3, 1, 3))))),
        Arguments.of(
            Named.of(
                "Backups of arbitrary nodes and partitions",
                new WildcardTestParameter(
                    new BackupIdentifierWildcardImpl(
                        Optional.empty(), Optional.empty(), CheckpointPattern.of(1L)),
                    List.of(
                        new BackupIdentifierImpl(1, 3, 1),
                        new BackupIdentifierImpl(2, 2, 1),
                        new BackupIdentifierImpl(3, 1, 1)),
                    List.of(
                        new BackupIdentifierImpl(1, 2, 2), new BackupIdentifierImpl(2, 1, 3))))),
        Arguments.of(
            Named.of(
                "Backups of arbitrary nodes and checkpoints",
                new WildcardTestParameter(
                    new BackupIdentifierWildcardImpl(
                        Optional.empty(), Optional.of(1), CheckpointPattern.any()),
                    List.of(
                        new BackupIdentifierImpl(1, 1, 3),
                        new BackupIdentifierImpl(2, 1, 2),
                        new BackupIdentifierImpl(3, 1, 1)),
                    List.of(
                        new BackupIdentifierImpl(1, 2, 2), new BackupIdentifierImpl(2, 3, 3))))),
        Arguments.of(
            Named.of(
                "All backups",
                new WildcardTestParameter(
                    new BackupIdentifierWildcardImpl(
                        Optional.empty(), Optional.empty(), CheckpointPattern.any()),
                    List.of(
                        new BackupIdentifierImpl(1, 1, 1),
                        new BackupIdentifierImpl(2, 2, 2),
                        new BackupIdentifierImpl(3, 3, 3)),
                    List.of()))));
  }

  public record WildcardTestParameter(
      BackupIdentifierWildcard wildcard,
      List<BackupIdentifierImpl> expectedIds,
      List<BackupIdentifierImpl> unexpectedIds) {}
}
