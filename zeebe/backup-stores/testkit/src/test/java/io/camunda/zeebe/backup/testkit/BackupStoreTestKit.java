/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.backup.testkit;

import io.camunda.zeebe.backup.testkit.support.TestBackupProvider;
import java.util.stream.Stream;
import org.junit.jupiter.params.provider.Arguments;

public interface BackupStoreTestKit
    extends SavingBackup,
        DeletingBackup,
        RestoringBackup,
        UpdatingBackupStatus,
        QueryingBackupStatus,
        ListingBackups,
        StoringRangeMarkers,
        StoringBackupIndex {

  static Stream<? extends Arguments> provideBackups() throws Exception {
    return TestBackupProvider.provideArguments();
  }
}
