/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms;

import com.github.vertical_blank.sqlformatter.SqlFormatter;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Set;
import liquibase.Liquibase;
import liquibase.database.DatabaseFactory;
import liquibase.resource.ClassLoaderResourceAccessor;
import liquibase.sqlgenerator.SqlGeneratorFactory;

public class LiquibaseScriptGenerator {

  public static void main(final String[] args) throws Exception {
    final var targetDir = args[0] + "/liquibase";
    final var databases = Set.of("h2", "mysql", "postgresql");

    for (final var database : databases) {
      generateLiquibaseScript(
          database, "db/changelog/rdbms-support/changelog-master.xml", targetDir, "master.sql");

      generateLiquibaseScript(
          database,
          "db/changelog/rdbms-support/8_8_0/8.8.0_master.xml",
          targetDir,
          "8.8.0_master.sql");
    }
  }

  public static void generateLiquibaseScript(
      final String databaseType,
      final String changesetFile,
      final String targetBaseDir,
      final String outputFileName)
      throws Exception {
    final var database = DatabaseFactory.getInstance().getDatabase(databaseType);

    final Liquibase liquibase =
        new Liquibase(changesetFile, new ClassLoaderResourceAccessor(), database);

    final var changelog = liquibase.getDatabaseChangeLog();

    final var sqlGenerator = SqlGeneratorFactory.getInstance();

    final var fileContent = new StringBuilder();

    for (final var changeSet : changelog.getChangeSets()) {
      fileContent.append("-- ");
      fileContent.append(changeSet.getId());
      fileContent.append("\n");

      for (final var change : changeSet.getChanges()) {
        final var sql = sqlGenerator.generateSql(change, database);
        for (final var s : sql) {
          final var formattedSql = SqlFormatter.format(s.toSql());

          fileContent.append(formattedSql);
          fileContent.append(";");
          fileContent.append("\n");
        }

        fileContent.append("\n");
      }
    }

    final String basedir = targetBaseDir + "/" + databaseType;
    Files.createDirectories(Paths.get(basedir));
    Files.write(
        Paths.get(basedir + "/" + outputFileName),
        fileContent.toString().getBytes(),
        StandardOpenOption.CREATE,
        StandardOpenOption.TRUNCATE_EXISTING);
  }
}
