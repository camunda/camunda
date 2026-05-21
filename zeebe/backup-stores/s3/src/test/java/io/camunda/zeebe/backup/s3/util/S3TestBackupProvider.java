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

import io.camunda.zeebe.backup.testkit.support.TestBackupProvider;
import java.util.stream.Stream;
import org.junit.jupiter.params.provider.Arguments;

public class S3TestBackupProvider extends TestBackupProvider {

  public static Stream<? extends Arguments> provideArguments() throws Exception {
    return Stream.of(
        arguments(named("stub", simpleBackup())),
        arguments(named("stub without snapshot", backupWithoutSnapshot())));
  }
}
