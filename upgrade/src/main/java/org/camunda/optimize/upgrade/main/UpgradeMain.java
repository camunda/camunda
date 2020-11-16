/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.upgrade.main;

import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.jetty.util.LoggingConfigurationReader;
import org.camunda.optimize.service.metadata.PreviousVersion;
import org.camunda.optimize.service.metadata.Version;
import org.camunda.optimize.upgrade.exception.UpgradeRuntimeException;
import org.camunda.optimize.upgrade.main.impl.GenericUpgradeProcedure;
import org.camunda.optimize.upgrade.main.impl.UpgradeFrom32To33;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Scanner;
import java.util.Set;

@Slf4j
public class UpgradeMain {

  private static final Set<String> ANSWER_OPTIONS_YES = Collections.unmodifiableSet(
    new HashSet<>(Arrays.asList("y", "yes"))
  );

  private static final Set<String> ANSWER_OPTIONS_NO = Collections.unmodifiableSet(
    new HashSet<>(Arrays.asList("n", "no"))
  );

  private static final Map<String, UpgradeProcedure> UPGRADE_PROCEDURES = new HashMap<>();

  static {
    new LoggingConfigurationReader().defineLogbackLoggingConfiguration();
    UPGRADE_PROCEDURES.put(
      Version.VERSION,
      new GenericUpgradeProcedure(PreviousVersion.PREVIOUS_VERSION, Version.VERSION)
    );
    UPGRADE_PROCEDURES.put(UpgradeFrom32To33.TO_VERSION, new UpgradeFrom32To33());
  }

  public static void main(String... args) {

    String targetVersion = Arrays.stream(args)
      .filter(arg -> arg.matches("\\d\\.\\d\\.\\d"))
      .findFirst()
      .orElse(Version.VERSION);
    UpgradeProcedure upgradeProcedure = UPGRADE_PROCEDURES.get(targetVersion);

    if (upgradeProcedure == null) {
      String errorMessage =
        "It was not possible to upgrade Optimize to version " + targetVersion + ".\n" +
          "Either this is the wrong upgrade jar or the jar is flawed. \n" +
          "Please contact the Optimize support for help!";
      throw new UpgradeRuntimeException(errorMessage);
    }

    if (Arrays.stream(args).noneMatch(arg -> arg.contains("skip-warning"))) {
      printWarning(upgradeProcedure.getInitialVersion(), upgradeProcedure.getTargetVersion());
    }

    log.info("Executing upgrade...");
    try {
      upgradeProcedure.performUpgrade();
      System.exit(0);
    } catch (final Exception e) {
      log.error(e.getMessage(), e);
      System.exit(2);
    }
  }

  private static void printWarning(String fromVersion, String toVersion) {
    String message =
      "\n\n" +
        "================================ WARNING! ================================ \n\n" +
        "Please be aware that you are about to upgrade the Optimize data \n" +
        "schema in Elasticsearch from version %s to %s. \n" +
        "There is no warranty that this upgrade might not break the data \n" +
        "structure in Elasticsearch. Therefore, it is highly recommended to \n" +
        "create a backup of your data in Elasticsearch in case something goes wrong. \n" +
        "\n" +
        "Do you want to proceed? [(y)es/(n)o] \n" +
        "\n" +
        "1. (y)es = I already did a backup and want to proceed. \n" +
        "2. (n)o = Thanks for reminding me, I want to do a backup first. \n" +
        "\n" +
        "Your answer (type your answer and hit enter): ";

    message = String.format(
      message,
      fromVersion,
      toVersion
    );
    System.out.println(message);

    String answer = "";
    while (!ANSWER_OPTIONS_YES.contains(answer)) {
      Scanner console = new Scanner(System.in);
      answer = console.next().trim().toLowerCase();
      if (ANSWER_OPTIONS_NO.contains(answer)) {
        System.out.println("The Optimize upgrade was aborted.");
        System.exit(1);
      } else if (!ANSWER_OPTIONS_YES.contains(answer)) {
        String text = "Your answer was '" + answer + "'. The only accepted answers are '(y)es' or '(n)o'. \n" +
          "\n" +
          "Your answer (type your answer and hit enter): ";
        System.out.println(text);
      }
    }
  }
}
