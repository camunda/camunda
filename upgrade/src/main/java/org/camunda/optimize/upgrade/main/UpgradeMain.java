/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.upgrade.main;

import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.service.metadata.Version;
import org.camunda.optimize.upgrade.exception.UpgradeRuntimeException;
import org.camunda.optimize.upgrade.plan.UpgradeExecutionDependencies;
import org.camunda.optimize.upgrade.plan.UpgradePlan;
import org.camunda.optimize.upgrade.plan.UpgradePlanRegistry;
import org.camunda.optimize.util.jetty.LoggingConfigurationReader;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Scanner;
import java.util.Set;

import static org.camunda.optimize.upgrade.util.UpgradeUtil.createUpgradeDependencies;

@Slf4j
public class UpgradeMain {

  private static final Set<String> ANSWER_OPTIONS_YES = Collections.unmodifiableSet(
    new HashSet<>(Arrays.asList("y", "yes"))
  );

  private static final Set<String> ANSWER_OPTIONS_NO = Collections.unmodifiableSet(
    new HashSet<>(Arrays.asList("n", "no"))
  );

  static {
    new LoggingConfigurationReader().defineLogbackLoggingConfiguration();
  }

  public static void main(String... args) {
    try {
      final UpgradeExecutionDependencies upgradeDependencies = createUpgradeDependencies();
      final UpgradeProcedure upgradeProcedure = UpgradeProcedureFactory.create(upgradeDependencies);
      final String targetVersion = Arrays.stream(args)
        .filter(arg -> arg.matches("\\d\\.\\d\\.\\d"))
        .findFirst()
        .orElse(Version.VERSION);

      final List<UpgradePlan> upgradePlans =
        new UpgradePlanRegistry(upgradeDependencies).getSequentialUpgradePlansToTargetVersion(targetVersion);

      if (upgradePlans.isEmpty()) {
        String errorMessage =
          "It was not possible to update Optimize to version " + targetVersion + ".\n" +
            "Either this is the wrong upgrade jar or the jar is flawed. \n" +
            "Please contact the Optimize support for help!";
        throw new UpgradeRuntimeException(errorMessage);
      }

      if (Arrays.stream(args).noneMatch(arg -> arg.contains("skip-warning"))) {
        printWarning(upgradePlans.get(upgradePlans.size() - 1).getToVersion().getValue());
      }

      log.info("Executing update...");

      for (UpgradePlan upgradePlan : upgradePlans) {
        upgradeProcedure.performUpgrade(upgradePlan);
      }
      upgradeProcedure.schemaUpgradeClient.initializeSchema();

      log.info("Update finished successfully.");

      System.exit(0);
    } catch (final Exception e) {
      log.error(e.getMessage(), e);
      System.exit(1);
    }
  }

  private static void printWarning(String toVersion) {
    String message =
      "\n\n" +
        "================================ WARNING! ================================ \n\n" +
        "Please be aware that you are about to update the Optimize data \n" +
        "schema in Elasticsearch to version %s. \n" +
        "There is no warranty that this update might not break the data \n" +
        "structure in Elasticsearch. Therefore, it is highly recommended to \n" +
        "create a backup of your data in Elasticsearch in case something goes wrong. \n" +
        "\n" +
        "Do you want to proceed? [(y)es/(n)o] \n" +
        "\n" +
        "1. (y)es = I already did a backup and want to proceed. \n" +
        "2. (n)o = Thanks for reminding me, I want to do a backup first. \n" +
        "\n" +
        "Your answer (type your answer and hit enter): ";

    message = String.format(message, toVersion);
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
