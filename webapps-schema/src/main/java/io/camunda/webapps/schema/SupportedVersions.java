/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.webapps.schema;

public final class SupportedVersions {

  /**
   * Official supported Elasticsearch version.
   *
   * <p>To be used for client and server dependencies (e.g. in test container tests).
   */
  public static final String SUPPORTED_ELASTICSEARCH_VERSION = "8.16.4";

  private SupportedVersions() {}
}
