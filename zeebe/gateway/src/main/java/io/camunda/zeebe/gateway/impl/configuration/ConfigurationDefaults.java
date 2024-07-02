/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.impl.configuration;

import java.time.Duration;

public final class ConfigurationDefaults {

  public static final String DEFAULT_HOST = "0.0.0.0";
  public static final int DEFAULT_PORT = 26500;

  public static final String DEFAULT_CONTACT_POINT_HOST = "127.0.0.1";
  public static final int DEFAULT_CONTACT_POINT_PORT = 26502;

  public static final String DEFAULT_MAX_MESSAGE_SIZE = "4M";
  public static final int DEFAULT_MAX_MESSAGE_COUNT = 16;
  public static final Duration DEFAULT_REQUEST_TIMEOUT = Duration.ofSeconds(15);
  public static final boolean DEFAULT_LONG_POLLING_ENABLED = true;
  public static final long DEFAULT_LONG_POLLING_TIMEOUT = 10_000;
  public static final int DEFAULT_LONG_POLLING_EMPTY_RESPONSE_THRESHOLD = 3;
  public static final boolean DEFAULT_TLS_ENABLED = false;
  public static final long DEFAULT_PROBE_TIMEOUT = 10_000; // 10 seconds

  public static final String DEFAULT_CLUSTER_NAME = "zeebe-cluster";
  public static final String DEFAULT_CLUSTER_MEMBER_ID = "gateway";
  public static final String DEFAULT_CLUSTER_HOST = "0.0.0.0";
  public static final int DEFAULT_CLUSTER_PORT = 26502;

  public static final int DEFAULT_MANAGEMENT_THREADS = 1;

  public static final String DEFAULT_KEEP_ALIVE_INTERVAL = "30s";
}
