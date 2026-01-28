/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.backup.s3.util;

import static io.camunda.zeebe.backup.testkit.support.TestBackupProvider.simpleBackup;
import static org.junit.jupiter.api.Named.named;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import io.camunda.zeebe.backup.api.Backup;
import io.camunda.zeebe.backup.common.BackupIdentifierImpl;
import io.camunda.zeebe.backup.testkit.support.TestBackupProvider;
import io.camunda.zeebe.util.VersionUtil;
import java.io.IOException;
import java.util.stream.Stream;
import org.junit.jupiter.params.provider.Arguments;

public class S3TestBackupProvider extends TestBackupProvider {

  static String version(final boolean legacy) {
    return legacy ? VersionUtil.getPreviousVersion() : VersionUtil.getVersion();
  }

  public static Stream<? extends Arguments> provideArguments() throws Exception {
    return Stream.of(
        arguments(named("stub", simpleBackup(false))),
        arguments(named("stub legacy", simpleBackup(true))),
        arguments(named("stub without snapshot", backupWithoutSnapshot(false))),
        arguments(named("stub without snapshot legacy", backupWithoutSnapshot(true))));
  }

  public static Backup simpleBackup(final boolean legacy) throws IOException {
    return simpleBackup(version(legacy));
  }

  public static Backup simpleBackupWithId(final BackupIdentifierImpl id, final boolean legacy)
      throws IOException {
    return simpleBackupWithId(id, version(legacy));
  }

  public static Backup backupWithoutSnapshot(final boolean legacy) throws IOException {
    return backupWithoutSnapshot(version(legacy));
  }
}
