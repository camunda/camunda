/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.gateway.impl.configuration.legacy;

@Deprecated(since = "0.23.0-alpha1")
/* Kept in order to be able to offer a migration path for old configuration.
 * It is not yet clear whether we intent to offer a migration path for old configurations.
 * This class might be moved or removed on short notice.
 */
public final class EnvironmentConstants {

  public static final String ENV_GATEWAY_HOST = "ZEEBE_GATEWAY_HOST";
  public static final String ENV_GATEWAY_PORT = "ZEEBE_GATEWAY_PORT";
  public static final String ENV_GATEWAY_MAX_MESSAGE_SIZE = "ZEEBE_GATEWAY_MAX_MESSAGE_SIZE";
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
  public static final String ENV_GATEWAY_SECURITY_ENABLED = "ZEEBE_GATEWAY_SECURITY_ENABLED";
  public static final String ENV_GATEWAY_CERTIFICATE_PATH = "ZEEBE_GATEWAY_CERTIFICATE_PATH";
  public static final String ENV_GATEWAY_PRIVATE_KEY_PATH = "ZEEBE_GATEWAY_PRIVATE_KEY_PATH";
  public static final String ENV_GATEWAY_KEEP_ALIVE_INTERVAL = "ZEEBE_GATEWAY_KEEP_ALIVE_INTERVAL";
}
