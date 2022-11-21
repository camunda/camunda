/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.util;

import lombok.experimental.UtilityClass;

import static org.camunda.optimize.service.metadata.Version.VERSION;

@UtilityClass
public class SnapshotUtil {
  public static final String REPOSITORY_MISSING_EXCEPTION_TYPE = "type=repository_missing_exception";
  public static final String SNAPSHOT_MISSING_EXCEPTION_TYPE = "type=snapshot_missing_exception";
  private static final String COMPONENT_PREFIX = "camunda_optimize_";
  private static final String SNAPSHOT_PREFIX = "{componentPrefix}{backupId}_"; // trailing underscore required to avoid
  // matching backupIds starting with the same characters
  private static final String SNAPSHOT_1_NAME_TEMPLATE = "{prefix}{version}_part_1_of_2"; // import indices
  private static final String SNAPSHOT_2_NAME_TEMPLATE = "{prefix}{version}_part_2_of_2"; // other indices

  public static String getSnapshotNameForImportIndices(final String backupId) {
    return getSnapshotName(SNAPSHOT_1_NAME_TEMPLATE, backupId);
  }

  public static String getSnapshotNameForNonImportIndices(final String backupId) {
    return getSnapshotName(SNAPSHOT_2_NAME_TEMPLATE, backupId);
  }

  public static String getSnapshotPrefixWithBackupId(final String backupId) {
    return SNAPSHOT_PREFIX
      .replace("{componentPrefix}", COMPONENT_PREFIX)
      .replace("{backupId}", backupId);
  }

  public static String getBackupIdFromSnapshotName(final String snapshotName) {
    final String snapshotNameWithoutComponentPrefix = snapshotName.substring(COMPONENT_PREFIX.length());
    return snapshotNameWithoutComponentPrefix.substring(0, snapshotName.indexOf("_") + 1);
  }

  public static String[] getAllWildcardedSnapshotNamesForBackupId(final String backupId) {
    return new String[]{getSnapshotNameWithVersionWildcard(SNAPSHOT_1_NAME_TEMPLATE, backupId),
      getSnapshotNameWithVersionWildcard(SNAPSHOT_2_NAME_TEMPLATE, backupId)};
  }

  private static String getSnapshotNameWithVersionWildcard(final String snapshotNameTemplate, final String backupId) {
    return snapshotNameTemplate
      .replace("{prefix}", getSnapshotPrefixWithBackupId(backupId))
      .replace("{version}", "*");
  }

  private static String getSnapshotName(final String snapshotNameTemplate, final String backupId) {
    return snapshotNameTemplate
      .replace("{prefix}", getSnapshotPrefixWithBackupId(backupId))
      .replace("{version}", VERSION);
  }
}
