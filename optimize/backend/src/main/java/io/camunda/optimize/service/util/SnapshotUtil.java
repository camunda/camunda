/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.util;

import static io.camunda.optimize.service.metadata.Version.VERSION;

import io.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;

public class SnapshotUtil {

  public static final String REPOSITORY_MISSING_EXCEPTION_TYPE = "repository_missing_exception";
  public static final String SNAPSHOT_MISSING_EXCEPTION_TYPE = "snapshot_missing_exception";
  private static final String COMPONENT_PREFIX = "camunda_optimize_";
  private static final String SNAPSHOT_PREFIX =
      "{componentPrefix}{backupId}_"; // trailing underscore required to avoid
  // matching backupIds starting with the same characters
  private static final String SNAPSHOT_1_NAME_TEMPLATE =
      "{prefix}{version}_part_1_of_2"; // import indices
  private static final String SNAPSHOT_2_NAME_TEMPLATE =
      "{prefix}{version}_part_2_of_2"; // other indices
  private static final String PREFIX_PLACEHOLDER = "{prefix}";
  private static final String COMPONENT_PREFIX_PLACEHOLDER = "{componentPrefix}";
  private static final String ID_PLACEHOLDER = "{backupId}";
  private static final String VERSION_PLACEHOLDER = "{version}";
  private static final Logger LOG = org.slf4j.LoggerFactory.getLogger(SnapshotUtil.class);

  public static String getSnapshotNameForImportIndices(final Long backupId) {
    return getSnapshotName(SNAPSHOT_1_NAME_TEMPLATE, backupId);
  }

  public static String getSnapshotNameForNonImportIndices(final Long backupId) {
    return getSnapshotName(SNAPSHOT_2_NAME_TEMPLATE, backupId);
  }

  public static String getSnapshotPrefixWithBackupId(final Long backupId) {
    return SNAPSHOT_PREFIX
        .replace(COMPONENT_PREFIX_PLACEHOLDER, COMPONENT_PREFIX)
        .replace(ID_PLACEHOLDER, String.valueOf(backupId));
  }

  public static Long getBackupIdFromSnapshotName(final String snapshotName) {
    final Pattern pattern = Pattern.compile("^" + COMPONENT_PREFIX + "(\\d+)_.*$");
    final Matcher matcher = pattern.matcher(snapshotName);

    if (matcher.find()) {
      final String numberStr = matcher.group(1);
      try {
        return Long.parseLong(numberStr);
      } catch (final NumberFormatException e) {
        final String msg =
            String.format(
                "Cannot retrieve backupID from snapshot [%s] because the found backupID is not a valid integer.",
                snapshotName);
        LOG.error(msg);
        throw new OptimizeRuntimeException(msg, e);
      }
    } else {
      final String msg = String.format("No backupID found in snapshot [%s].", snapshotName);
      LOG.error(msg);
      throw new OptimizeRuntimeException(msg);
    }
  }

  public static String[] getAllWildcardedSnapshotNamesForBackupId(final Long backupId) {
    return new String[] {
      getSnapshotNameWithVersionWildcard(SNAPSHOT_1_NAME_TEMPLATE, backupId),
      getSnapshotNameWithVersionWildcard(SNAPSHOT_2_NAME_TEMPLATE, backupId)
    };
  }

  public static String[] getAllWildcardedSnapshotNamesForWildcardedBackupId() {
    return new String[] {
      SNAPSHOT_1_NAME_TEMPLATE
          .replace(
              PREFIX_PLACEHOLDER,
              SNAPSHOT_PREFIX
                  .replace(COMPONENT_PREFIX_PLACEHOLDER, COMPONENT_PREFIX)
                  .replace(ID_PLACEHOLDER, "*"))
          .replace(VERSION_PLACEHOLDER, "*"),
      SNAPSHOT_2_NAME_TEMPLATE
          .replace(
              PREFIX_PLACEHOLDER,
              SNAPSHOT_PREFIX
                  .replace(COMPONENT_PREFIX_PLACEHOLDER, COMPONENT_PREFIX)
                  .replace(ID_PLACEHOLDER, "*"))
          .replace(VERSION_PLACEHOLDER, "*")
    };
  }

  private static String getSnapshotNameWithVersionWildcard(
      final String snapshotNameTemplate, final Long backupId) {
    return snapshotNameTemplate
        .replace(PREFIX_PLACEHOLDER, getSnapshotPrefixWithBackupId(backupId))
        .replace(VERSION_PLACEHOLDER, "*");
  }

  private static String getSnapshotName(final String snapshotNameTemplate, final Long backupId) {
    return snapshotNameTemplate
        .replace(PREFIX_PLACEHOLDER, getSnapshotPrefixWithBackupId(backupId))
        .replace(VERSION_PLACEHOLDER, VERSION);
  }
}
