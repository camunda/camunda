/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms;

import com.github.vertical_blank.sqlformatter.SqlFormatter;
import io.camunda.db.rdbms.config.VendorDatabasePropertiesLoader;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.Objects;
import java.util.stream.Collectors;
import liquibase.Liquibase;
import liquibase.change.AbstractSQLChange;
import liquibase.change.Change;
import liquibase.changelog.ChangeSet;
import liquibase.database.Database;
import liquibase.database.DatabaseFactory;
import liquibase.exception.LiquibaseException;
import liquibase.resource.ClassLoaderResourceAccessor;
import liquibase.sqlgenerator.SqlGeneratorFactory;
import org.apache.commons.lang3.StringUtils;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

public class LiquibaseScriptGenerator {

  public static final String H2 = "h2";
  public static final String MARIADB = "mariadb";
  public static final String MYSQL = "mysql";
  public static final String MSSQL = "mssql";
  public static final String POSTGRESQL = "postgresql";
  public static final String ORACLE = "oracle";

  public static final String[] DATABASES = {H2, MARIADB, MYSQL, MSSQL, POSTGRESQL, ORACLE};
  public static final String CHANGELOG_PATH = "db/changelog/rdbms-exporter/";
  public static final String CHANGESET_PATH = CHANGELOG_PATH + "changesets/";

  /**
   * Generates Liquibase SQL scripts for a given database type.
   *
   * <p>arg0: target directory arg1: project version arg2: table prefix (optional)
   */
  public static void main(final String[] args) throws Exception {
    final var targetDir = args[0] + "/liquibase/sql";
    final var projectVersion = args[1];

    final var prefix = args.length >= 3 ? args[2] : "";

    for (final var database : DATABASES) {
      // We generate create scripts for the latest version from the changelog-master.xml
      generateLiquibaseScript(
          database,
          CHANGELOG_PATH + "changelog-master.xml",
          prefix,
          targetDir + "/create",
          database + "_master.sql");

      // We generate upgrade scripts for each version (except 8.9.0) from the changesets, so that
      // customers, who created their schema with an older version can upgrade to the latest
      // version step by step
      final var upgradeChangesets =
          Arrays.stream(
                  new PathMatchingResourcePatternResolver()
                      .getResources("classpath*:" + CHANGESET_PATH + "*.xml"))
              .filter(resource -> !Objects.equals(resource.getFilename(), "8.9.0.xml"))
              .map(it -> CHANGESET_PATH + it.getFilename())
              .collect(Collectors.toSet());

      // 8.9.0 is the initial version of the RDBMS schema, so we start upgrades from there
      String previousVersion = "8.9.0";

      for (final var changesetFile : upgradeChangesets) {
        final String fileName = Paths.get(changesetFile).getFileName().toString();
        final String sqlOutputDir = targetDir + "/upgrade";
        generateLiquibaseScript(
            database,
            changesetFile,
            prefix,
            sqlOutputDir,
            String.format(
                "%s_upgrade_%s_to_%s.sql",
                database, previousVersion, fileName.replace(".xml", "")));

        previousVersion = fileName.replace(".xml", "");
      }
    }
  }

  public static void generateLiquibaseScript(
      final String databaseType,
      final String changesetFile,
      final String prefix,
      final String targetBaseDir,
      final String outputFileName)
      throws Exception {
    final var properties = VendorDatabasePropertiesLoader.load(databaseType);

    final var sqlScript =
        generateSqlScript(
            databaseType,
            changesetFile,
            prefix,
            properties.userCharColumnSize(),
            properties.errorMessageSize(),
            properties.treePathSize());

    final String basedir = targetBaseDir + "/" + databaseType;
    Files.createDirectories(Paths.get(basedir));
    Files.write(
        Paths.get(basedir + "/" + outputFileName),
        sqlScript.getBytes(),
        StandardOpenOption.CREATE,
        StandardOpenOption.TRUNCATE_EXISTING);
  }

  public static String generateSqlScript(
      final String databaseType,
      final String changesetFile,
      final String prefix,
      final int userCharColumnSize,
      final int errorMessageSize,
      final int treePathSize)
      throws LiquibaseException {

    final var database = DatabaseFactory.getInstance().getDatabase(databaseType);

    final Liquibase liquibase =
        new Liquibase(changesetFile, new ClassLoaderResourceAccessor(), database);
    liquibase.setChangeLogParameter("prefix", prefix);
    liquibase.setChangeLogParameter("userCharColumnSize", userCharColumnSize);
    liquibase.setChangeLogParameter("errorMessageSize", errorMessageSize);
    liquibase.setChangeLogParameter("treePathSize", treePathSize);

    final var changelog = liquibase.getDatabaseChangeLog();

    final var sqlGenerator = SqlGeneratorFactory.getInstance();

    final var sqlScript = new StringBuilder();

    for (final var changeSet : changelog.getChangeSets()) {
      processChangeSet(changeSet, sqlScript, sqlGenerator, database);
    }

    return sqlScript.toString();
  }

  private static void processChangeSet(
      final ChangeSet changeSet,
      final StringBuilder sqlScript,
      final SqlGeneratorFactory sqlGenerator,
      final Database database) {
    sqlScript.append("-- ");
    sqlScript.append(changeSet.getId());
    sqlScript.append("\n");

    for (final var change : changeSet.getChanges()) {
      if (!isSupported(change, database)) {
        continue;
      }

      final var sql = sqlGenerator.generateSql(change, database);

      for (final var s : sql) {
        var sqlString = s.toSql();

        // Apply SQL visitors (which handle modifySql directives)
        for (final var visitor : changeSet.getSqlVisitors()) {
          if (visitor.getApplicableDbms().contains(database.getShortName())) {
            sqlString = visitor.modifySql(sqlString, database);
          }
        }

        sqlScript.append(formatSql(sqlString));
        sqlScript.append(";");
        sqlScript.append("\n");
      }

      sqlScript.append("\n");
    }
  }

  private static String formatSql(final String sqlString) {
    var formattedSql = SqlFormatter.format(sqlString);

    // Fix spaces inside backticked identifiers (e.g., ` DESCRIPTION ` -> `DESCRIPTION`)
    // Match backtick pairs and remove leading/trailing whitespace inside them
    formattedSql = formattedSql.replaceAll("`\\s+([^`]+?)\\s+`", "`$1`");
    return formattedSql;
  }

  private static boolean isSupported(final Change change, final Database database) {
    return switch (change) {
      case final AbstractSQLChange sqlChange -> isSupported(sqlChange.getDbms(), database);
      default -> true; // For other change types, assume they are supported
    };
  }

  private static boolean isSupported(final String dbms, final Database database) {
    return StringUtils.isEmpty(dbms)
        || Arrays.stream(dbms.split(",")).anyMatch(d -> d.equals(database.getShortName()));
  }
}
