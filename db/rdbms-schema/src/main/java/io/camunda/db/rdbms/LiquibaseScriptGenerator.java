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
import liquibase.exception.LiquibaseException;
import liquibase.resource.ClassLoaderResourceAccessor;
import liquibase.sqlgenerator.SqlGeneratorFactory;

public class LiquibaseScriptGenerator {

  /**
   * Generates Liquibase SQL scripts for a given database type. arg0: target directory arg1: table
   * prefix (optional)
   */
  public static void main(final String[] args) throws Exception {
    final var targetDir = args[0] + "/liquibase";

    final var prefix = args.length >= 2 ? args[1] : "";
    final var databases = Set.of("h2", "mysql", "postgresql", "oracle");

    for (final var database : databases) {
      generateLiquibaseScript(
          database,
          "db/changelog/rdbms-exporter/changelog-master.xml",
          prefix,
          targetDir,
          "master.sql");

      generateLiquibaseScript(
          database,
          "db/changelog/rdbms-exporter/changesets/8.8.0.xml",
          prefix,
          targetDir,
          "8.8.0.sql");
    }
  }

  public static void generateLiquibaseScript(
      final String databaseType,
      final String changesetFile,
      final String prefix,
      final String targetBaseDir,
      final String outputFileName)
      throws Exception {
    final var sqlScript = generateSqlScript(databaseType, changesetFile, prefix);

    final String basedir = targetBaseDir + "/" + databaseType;
    Files.createDirectories(Paths.get(basedir));
    Files.write(
        Paths.get(basedir + "/" + outputFileName),
        sqlScript.getBytes(),
        StandardOpenOption.CREATE,
        StandardOpenOption.TRUNCATE_EXISTING);
  }

  public static String generateSqlScript(
      final String databaseType, final String changesetFile, final String prefix)
      throws LiquibaseException {

    final var database = DatabaseFactory.getInstance().getDatabase(databaseType);

    final Liquibase liquibase =
        new Liquibase(changesetFile, new ClassLoaderResourceAccessor(), database);
    liquibase.setChangeLogParameter("prefix", prefix);

    final var changelog = liquibase.getDatabaseChangeLog();

    final var sqlGenerator = SqlGeneratorFactory.getInstance();

    final var sqlScript = new StringBuilder();

    for (final var changeSet : changelog.getChangeSets()) {
      sqlScript.append("-- ");
      sqlScript.append(changeSet.getId());
      sqlScript.append("\n");

      for (final var change : changeSet.getChanges()) {
        final var sql = sqlGenerator.generateSql(change, database);
        for (final var s : sql) {
          final var formattedSql = SqlFormatter.format(s.toSql());

          sqlScript.append(formattedSql);
          sqlScript.append(";");
          sqlScript.append("\n");
        }

        sqlScript.append("\n");
      }
    }

    return sqlScript.toString();
  }
}
