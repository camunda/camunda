/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.util;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.camunda.optimize.service.metadata.Version.VERSION;

@Slf4j
@UtilityClass
public class SnapshotUtil {
  public static final String REPOSITORY_MISSING_EXCEPTION_TYPE = "type=repository_missing_exception";
  public static final String SNAPSHOT_MISSING_EXCEPTION_TYPE = "type=snapshot_missing_exception";
  private static final String COMPONENT_PREFIX = "camunda_optimize_";
  private static final String SNAPSHOT_PREFIX = "{componentPrefix}{backupId}_"; // trailing underscore required to avoid
  // matching backupIds starting with the same characters
  private static final String SNAPSHOT_1_NAME_TEMPLATE = "{prefix}{version}_part_1_of_2"; // import indices
  private static final String SNAPSHOT_2_NAME_TEMPLATE = "{prefix}{version}_part_2_of_2"; // other indices
  private static final String PREFIX_PLACEHOLDER = "{prefix}";
  private static final String COMPONENT_PREFIX_PLACEHOLDER = "{componentPrefix}";
  private static final String ID_PLACEHOLDER = "{backupId}";
  private static final String VERSION_PLACEHOLDER = "{version}";

  public static String getSnapshotNameForImportIndices(final Integer backupId) {
    return getSnapshotName(SNAPSHOT_1_NAME_TEMPLATE, backupId);
  }

  public static String getSnapshotNameForNonImportIndices(final Integer backupId) {
    return getSnapshotName(SNAPSHOT_2_NAME_TEMPLATE, backupId);
  }

  public static String getSnapshotPrefixWithBackupId(final Integer backupId) {
    return SNAPSHOT_PREFIX
      .replace(COMPONENT_PREFIX_PLACEHOLDER, COMPONENT_PREFIX)
      .replace(ID_PLACEHOLDER, String.valueOf(backupId));
  }

  public static Integer getBackupIdFromSnapshotName(final String snapshotName) {
    Pattern pattern = Pattern.compile("^" + COMPONENT_PREFIX + "(\\d+)_.*$");
    Matcher matcher = pattern.matcher(snapshotName);

    if (matcher.find()) {
      String numberStr = matcher.group(1);
      try {
        return Integer.parseInt(numberStr);
      } catch (NumberFormatException e) {
        final String msg = String.format(
          "Cannot retrieve backupID from snapshot [%s] because the found backupID is not a valid integer.",
          snapshotName
        );
        log.error(msg);
        throw new OptimizeRuntimeException(msg, e);
      }
    } else {
      final String msg = String.format("No backupID found in snapshot [%s].", snapshotName);
      log.error(msg);
      throw new OptimizeRuntimeException(msg);
    }
  }

  public static String[] getAllWildcardedSnapshotNamesForBackupId(final Integer backupId) {
    return new String[]{getSnapshotNameWithVersionWildcard(SNAPSHOT_1_NAME_TEMPLATE, backupId),
      getSnapshotNameWithVersionWildcard(SNAPSHOT_2_NAME_TEMPLATE, backupId)};
  }

  public static String[] getAllWildcardedSnapshotNamesForWildcardedBackupId() {
    return new String[]{
      SNAPSHOT_1_NAME_TEMPLATE
        .replace(PREFIX_PLACEHOLDER, SNAPSHOT_PREFIX
          .replace(COMPONENT_PREFIX_PLACEHOLDER, COMPONENT_PREFIX)
          .replace(ID_PLACEHOLDER, "*"))
        .replace(VERSION_PLACEHOLDER, "*"),
      SNAPSHOT_2_NAME_TEMPLATE
        .replace(PREFIX_PLACEHOLDER, SNAPSHOT_PREFIX
          .replace(COMPONENT_PREFIX_PLACEHOLDER, COMPONENT_PREFIX)
          .replace(ID_PLACEHOLDER, "*"))
        .replace(VERSION_PLACEHOLDER, "*")};
  }

  private static String getSnapshotNameWithVersionWildcard(final String snapshotNameTemplate, final Integer backupId) {
    return snapshotNameTemplate
      .replace(PREFIX_PLACEHOLDER, getSnapshotPrefixWithBackupId(backupId))
      .replace(VERSION_PLACEHOLDER, "*");
  }

  private static String getSnapshotName(final String snapshotNameTemplate, final Integer backupId) {
    return snapshotNameTemplate
      .replace(PREFIX_PLACEHOLDER, getSnapshotPrefixWithBackupId(backupId))
      .replace(VERSION_PLACEHOLDER, VERSION);
  }
}
