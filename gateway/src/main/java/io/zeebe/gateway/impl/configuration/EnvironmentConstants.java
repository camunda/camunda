/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.gateway.impl.configuration;

public class EnvironmentConstants {

  public static final String ENV_GATEWAY_HOST = "ZEEBE_GATEWAY_HOST";
  public static final String ENV_GATEWAY_PORT = "ZEEBE_GATEWAY_PORT";
  public static final String ENV_GATEWAY_TRANSPORT_BUFFER = "ZEEBE_GATEWAY_TRANSPORT_BUFFER";
  public static final String ENV_GATEWAY_REQUEST_TIMEOUT = "ZEEBE_GATEWAY_REQUEST_TIMEOUT";
  public static final String ENV_GATEWAY_MANAGEMENT_THREADS = "ZEEBE_GATEWAY_MANAGEMENT_THREADS";
  public static final String ENV_GATEWAY_CONTACT_POINT = "ZEEBE_GATEWAY_CONTACT_POINT";
  public static final String ENV_GATEWAY_CLUSTER_NAME = "ZEEBE_GATEWAY_CLUSTER_NAME";
  public static final String ENV_GATEWAY_CLUSTER_MEMBER_ID = "ZEEBE_GATEWAY_CLUSTER_MEMBER_ID";
  public static final String ENV_GATEWAY_CLUSTER_HOST = "ZEEBE_GATEWAY_CLUSTER_HOST";
  public static final String ENV_GATEWAY_CLUSTER_PORT = "ZEEBE_GATEWAY_CLUSTER_PORT";
  public static final String ENV_GATEWAY_MONITORING_ENABLED = "ZEEBE_GATEWAY_MONITORING_ENABLED";
  public static final String ENV_GATEWAY_MONITORING_HOST = "ZEEBE_GATEWAY_MONITORING_HOST";
  public static final String ENV_GATEWAY_MONITORING_PORT = "ZEEBE_GATEWAY_MONITORING_PORT";
  public static final String ENV_GATEWAY_BACKPRESSURE_ENABLED =
      "ZEEBE_GATEWAY_BACKPRESSURE_ENABLED";
  public static final String ENV_GATEWAY_BACKPRESSURE_AIMD_MIN_LIMIT =
      "ZEEBE_GATEWAY_BACKPRESSURE_AIMD_MINLIMIT";
  public static final String ENV_GATEWAY_BACKPRESSURE_AIMD_REQUEST_TIMEOUT =
      "ZEEBE_GATEWAY_BACKPRESSURE_AIMD_REQUESTTIMEOUT";
  public static final String ENV_GATEWAY_BACKPRESSURE_AIMD_INITIAL_LIMIT =
      "ZEEBE_GATEWAY_BACKPRESSURE_AIMD_INITIALLIMIT";
  public static final String ENV_GATEWAY_BACKPRESSURE_AIMD_MAX_LIMIT =
      "ZEEBE_GATEWAY_BACKPRESSURE_AIMD_MAXLIMIT";
  public static final String ENV_GATEWAY_BACKPRESSURE_AIMD_BACKOFF_RATIO =
      "ZEEBE_GATEWAY_BACKPRESSURE_AIMD_BACKOFFRATIO";
  public static final String ENV_GATEWAY_BACKPRESSURE_FIXED_LIMIT =
      "ZEEBE_GATEWAY_BACKPRESSURE_FIXEDLIMIT_LIMIT";
}
