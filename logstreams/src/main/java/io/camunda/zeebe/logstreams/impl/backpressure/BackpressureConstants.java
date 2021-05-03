/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.logstreams.impl.backpressure;

/**
 * This class should be later be located in the broker configs - due to the primitive usage
 * currently we are not able to access the BrokerCfg, this is the reason why the configuration is
 * only based on environment variables.
 *
 * <p>The constants should then be copied to EnvironmentConstants class.
 */
public final class BackpressureConstants {

  // BACK PRESSURE ON LOG APPENDER
  public static final String ENV_BP_APPENDER = "ZEEBE_BP_APPENDER";
  public static final String ENV_BP_APPENDER_WINDOWED = "ZEEBE_BP_APPENDER_WINDOWED";
  public static final String ENV_BP_APPENDER_ALGORITHM = "ZEEBE_BP_APPENDER_ALGORITHM";

  // APPEND LIMITER - VEGAS ALGORITHM
  public static final String ENV_BP_APPENDER_VEGAS_INIT_LIMIT =
      "ZEEBE_BP_APPENDER_VEGAS_INIT_LIMIT";
  public static final String ENV_BP_APPENDER_VEGAS_MAX_CONCURRENCY =
      "ZEEBE_BP_APPENDER_VEGAS_MAX_CONCURRENCY";
  public static final String ENV_BP_APPENDER_VEGAS_ALPHA_LIMIT =
      "ZEEBE_BP_APPENDER_VEGAS_ALPHA_LIMIT";
  public static final String ENV_BP_APPENDER_VEGAS_BETA_LIMIT =
      "ZEEBE_BP_APPENDER_VEGAS_BETA_LIMIT";

  // APPEND LIMITER - GRADIENT2 ALGORITHM
  public static final String ENV_BP_APPENDER_GRADIENT2_INIT_LIMIT =
      "ZEEBE_BP_APPENDER_GRADIENT2_INIT_LIMIT";
  public static final String ENV_BP_APPENDER_GRADIENT2_MAX_CONCURRENCY =
      "ZEEBE_BP_APPENDER_GRADIENT2_MAX_CONCURRENCY";
  public static final String ENV_BP_APPENDER_GRADIENT2_QUEUE_SIZE =
      "ZEEBE_BP_APPENDER_GRADIENT2_QUEUE_SIZE";
  public static final String ENV_BP_APPENDER_GRADIENT2_MIN_LIMIT =
      "ZEEBE_BP_APPENDER_VEGAS_BETA_LIMIT";
  public static final String ENV_BP_APPENDER_GRADIENT2_LONG_WINDOW =
      "ZEEBE_BP_APPENDER_GRADIENT2_LONG_WINDOW";
  public static final String ENV_BP_APPENDER_GRADIENT2_RTT_TOLERANCE =
      "ZEEBE_BP_APPENDER_GRADIENT2_RTT_TOLERANCE";
}
