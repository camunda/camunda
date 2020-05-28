/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe;

import java.util.Arrays;
import java.util.List;
import org.springframework.core.env.Environment;

public final class EnvironmentHelper {

  public static boolean isProductionEnvironment(final Environment springEnvironment) {
    boolean result = true;

    if (springEnvironment == null) {
      result = false;
    } else {
      final String[] activeProfiles = springEnvironment.getActiveProfiles();
      if (activeProfiles != null && !(activeProfiles.length == 0)) {
        final List<String> activeProfileList = Arrays.asList(activeProfiles);

        if (activeProfileList.contains("dev") || activeProfileList.contains("test")) {
          result = false;
        }
      }
    }

    return result;
  }

  /**
   * This method disables the gateway's health indicator and probes before launching a broker. This
   * is necessary, because broker and gateway share the same classpath. Therefore, all health
   * indicators targeted at the gateway, will by default also be enabled for the broker. This method
   * is here to prevent this.
   *
   * <p>Note that currently this is a very crude implementation which takes advantage of the fact
   * that no Spring health indicators are currently implemented for the broker. Therefore, we can
   * simply disable all. In the future, this implementation will need to become more sophisticated.
   *
   * <p>This method must be called by the broker before launching Spring system.
   */
  public static void disableGatewayHealthIndicatorsAndProbes() {
    System.setProperty("management.health.defaults.enabled", "false");
  }
}
