/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration;

import java.util.Set;

public class Filesystem {
  private static final String PREFIX = "camunda.data.backup.filesystem.";
  private static final Set<String> LEGACY__PROPERTIES =
      Set.of("zeebe.broker.data.backup.s3.bucketName");

  /**
   * Name of the bucket where the backup will be stored. The bucket must be already created. The
   * bucket must not be shared with other zeebe clusters. bucketName must not be empty.
   */
  private String bucketName;
}
