/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.schema.migration;

import java.util.Arrays;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.ExitCodeGenerator;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Profile;

@Profile("migration")
@SpringBootApplication
@ComponentScan(basePackages = { "org.camunda.operate.property", "org.camunda.operate.es", "org.camunda.operate.schema" })
public class SchemaMigration implements CommandLineRunner {

  @Autowired
  private Migrator migrator;
  
  private static int exitCode = 0;

  @Override
  public void run(String... args) {
    if (!migrator.migrate(extractArg(args, "migration.sourceVersion"), extractArg(args, "migration.destinationVersion"))) {
      exitCode = -1;
    } else {
      exitCode = 0;
    }
  }

  private String extractArg(final String[] args,final String argName) {
    final String[] destinationVersion = new String[1];
    Arrays.stream(args).filter(v -> v.contains(argName)).findFirst()
        .ifPresentOrElse(v -> {
              destinationVersion[0] = v.substring(v.indexOf("=") + 1);
            },
            () -> destinationVersion[0] = null
        );
    return destinationVersion[0];
  }

  public static void main(String[] args) {
    //To ensure that debug logging performed using java.util.logging is routed into Log4j2
    System.setProperty("java.util.logging.manager", "org.apache.logging.log4j.jul.LogManager");
    final SpringApplication springApplication = new SpringApplication(SchemaMigration.class);
    springApplication.setWebApplicationType(WebApplicationType.NONE);
    springApplication.setAddCommandLineProperties(true);
    springApplication.setAdditionalProfiles("migration");
    final ConfigurableApplicationContext ctx = springApplication.run(args); 
    SpringApplication.exit(ctx, new ExitCodeGenerator() {

      @Override
      public int getExitCode() {
        return exitCode;
      }
      
    });
  }
}
