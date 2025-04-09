/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.upgrade.main;

import static io.camunda.optimize.service.util.configuration.ConfigurationServiceConstants.CAMUNDA_OPTIMIZE_DATABASE;
import static io.camunda.optimize.service.util.configuration.ConfigurationServiceConstants.ELASTICSEARCH_DATABASE_PROPERTY;
import static io.camunda.optimize.upgrade.util.UpgradeUtil.createUpgradeDependencies;

import io.camunda.optimize.service.metadata.Version;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import io.camunda.optimize.service.util.configuration.ConfigurationServiceBuilder;
import io.camunda.optimize.service.util.configuration.DatabaseType;
import io.camunda.optimize.upgrade.exception.UpgradeRuntimeException;
import io.camunda.optimize.upgrade.plan.UpgradeExecutionDependencies;
import io.camunda.optimize.upgrade.plan.UpgradePlan;
import io.camunda.optimize.upgrade.plan.UpgradePlanRegistry;
import io.camunda.optimize.util.tomcat.LoggingConfigurationReader;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Scanner;
import java.util.Set;
import org.slf4j.Logger;

public class UpgradeMain {

  private static final Set<String> ANSWER_OPTIONS_YES =
      Collections.unmodifiableSet(new HashSet<>(Arrays.asList("y", "yes")));

  private static final Set<String> ANSWER_OPTIONS_NO =
      Collections.unmodifiableSet(new HashSet<>(Arrays.asList("n", "no")));
  private static final Logger LOG = org.slf4j.LoggerFactory.getLogger(UpgradeMain.class);

  public static void main(final String... args) {
    new LoggingConfigurationReader().defineLog4jLoggingConfiguration();

    try {
      final DatabaseType databaseType =
          ConfigurationService.convertToDatabaseProperty(
              Optional.ofNullable(System.getenv(CAMUNDA_OPTIMIZE_DATABASE))
                  .orElse(ELASTICSEARCH_DATABASE_PROPERTY));
      LOG.info("Identified {} Database configuration", databaseType.getId());

      final boolean initSchemaEnabled =
          ConfigurationServiceBuilder.createDefaultConfiguration()
              .getElasticSearchConfiguration()
              .getConnection()
              .isInitSchemaEnabled();

      final boolean clusterTaskCheckingEnabled =
          ConfigurationServiceBuilder.createDefaultConfiguration()
              .getElasticSearchConfiguration()
              .getConnection()
              .isClusterTaskCheckingEnabled();
      if (databaseType == DatabaseType.ELASTICSEARCH
          && (!initSchemaEnabled || !clusterTaskCheckingEnabled)) {
        throw new UpgradeRuntimeException(
            "Upgrade cannot be performed without cluster checking and schema initialization enabled");
      }

      final UpgradeExecutionDependencies upgradeDependencies =
          createUpgradeDependencies(databaseType);
      final UpgradeProcedure upgradeProcedure = UpgradeProcedureFactory.create(upgradeDependencies);
      final String targetVersion =
          Arrays.stream(args)
              .filter(arg -> arg.matches("\\d\\.\\d\\.\\d"))
              .findFirst()
              .orElse(Version.VERSION);

      final List<UpgradePlan> upgradePlans =
          new UpgradePlanRegistry(upgradeDependencies)
              .getSequentialUpgradePlansToTargetVersion(targetVersion);

      if (upgradePlans.isEmpty()) {
        final String errorMessage =
            "It was not possible to update Optimize to version "
                + targetVersion
                + ".\n"
                + "Either this is the wrong upgrade jar or the jar is flawed. \n"
                + "Please contact the Optimize support for help!";
        throw new UpgradeRuntimeException(errorMessage);
      }

      if (Arrays.stream(args).noneMatch(arg -> arg.contains("skip-warning"))) {
        printWarning(upgradePlans.get(upgradePlans.size() - 1).getToVersion().getValue());
      }

      LOG.info("Executing update...");

      for (final UpgradePlan upgradePlan : upgradePlans) {
        upgradeProcedure.performUpgrade(upgradePlan);
      }

      upgradeProcedure.schemaUpgradeClient.initializeSchema();

      LOG.info("Update finished successfully.");

      System.exit(0);
    } catch (final Exception e) {
      LOG.error(e.getMessage(), e);
      System.exit(1);
    }
  }

  private static void printWarning(final String toVersion) {
    String message =
        "\n\n"
            + "================================ WARNING! ================================ \n\n"
            + "Please be aware that you are about to update the Optimize data \n"
            + "schema in Elasticsearch to version %s. \n"
            + "There is no warranty that this update might not break the data \n"
            + "structure in Elasticsearch. Therefore, it is highly recommended to \n"
            + "create a backup of your data in Elasticsearch in case something goes wrong. \n"
            + "\n"
            + "Do you want to proceed? [(y)es/(n)o] \n"
            + "\n"
            + "1. (y)es = I already did a backup and want to proceed. \n"
            + "2. (n)o = Thanks for reminding me, I want to do a backup first. \n"
            + "\n"
            + "Your answer (type your answer and hit enter): ";

    message = String.format(message, toVersion);
    System.out.println(message);

    String answer = "";
    while (!ANSWER_OPTIONS_YES.contains(answer)) {
      final Scanner console = new Scanner(System.in);
      answer = console.next().trim().toLowerCase(Locale.ENGLISH);
      if (ANSWER_OPTIONS_NO.contains(answer)) {
        System.out.println("The Optimize upgrade was aborted.");
        System.exit(1);
      } else if (!ANSWER_OPTIONS_YES.contains(answer)) {
        final String text =
            "Your answer was '"
                + answer
                + "'. The only accepted answers are '(y)es' or '(n)o'. \n"
                + "\n"
                + "Your answer (type your answer and hit enter): ";
        System.out.println(text);
      }
    }
  }
}
