/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.gateway.impl.configuration;

public class ConfigurationDefaults {

  public static final String DEFAULT_HOST = "0.0.0.0";
  public static final int DEFAULT_PORT = 26500;
  public static final String DEFAULT_CONTACT_POINT_HOST = "127.0.0.1";
  public static final String DEFAULT_TRANSPORT_BUFFER_SIZE = "128M";
  public static final int DEFAULT_CONTACT_POINT_PORT = 26502;
  public static final int DEFAULT_MANAGEMENT_THREADS = 1;
  public static final String DEFAULT_REQUEST_TIMEOUT = "15s";
  public static final String DEFAULT_CLUSTER_NAME = "zeebe-cluster";
  public static final String DEFAULT_CLUSTER_MEMBER_ID = "gateway";
  public static final String DEFAULT_CLUSTER_HOST = "0.0.0.0";
  public static final int DEFAULT_CLUSTER_PORT = 26502;
  public static final boolean DEFAULT_MONITORING_ENABLED = false;
  public static final int DEFAULT_MONITORING_PORT = 9600;
}
