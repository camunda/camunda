/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms;

import io.camunda.db.rdbms.config.VendorDatabasePropertiesLoader;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Objects;
import liquibase.Liquibase;
import liquibase.change.AbstractSQLChange;
import liquibase.change.Change;
import liquibase.changelog.ChangeSet;
import liquibase.database.Database;
import liquibase.database.DatabaseFactory;
import liquibase.database.OfflineConnection;
import liquibase.exception.LiquibaseException;
import liquibase.resource.ClassLoaderResourceAccessor;
import liquibase.sqlgenerator.SqlGeneratorFactory;
import org.apache.commons.lang3.StringUtils;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

public class LiquibaseScriptGenerator {

  public static final String H2 = "h2";
  public static final String MARIADB = "mariadb";
  public static final String MYSQL = "mysql";
  public static final String MSSQL = "mssql";
  public static final String POSTGRESQL = "postgresql";
  public static final String ORACLE = "oracle";

  public static final String CHANGELOG_PATH = "db/changelog/rdbms-exporter/";
  public static final String CHANGESET_PATH = CHANGELOG_PATH + "changesets/";

  public static final DatabaseVersion[] DATABASE_VERSIONS = {
    new DatabaseVersion(H2),
    new DatabaseVersion(MARIADB),
    new DatabaseVersion(MYSQL),
    new DatabaseVersion(MSSQL),
    new DatabaseVersion(POSTGRESQL),
    // Oracle 19 target: generates NUMBER(1,0) for boolean columns (compatible with 19, 21, 23+)
    new DatabaseVersion(ORACLE, 19L),
  };

  /**
   * Generates Liquibase SQL scripts for a given database type.
   *
   * <p>arg0: target directory arg1: project version arg2: table prefix (optional)
   */
  public static void main(final String[] args) throws Exception {
    final var targetDir = args[0] + "/liquibase/sql";
    final var projectVersion = args[1];

    final var prefix = args.length >= 3 ? args[2] : "";

    // Identify upgrade changesets once (8.9.0 pre-dates rolling-upgrade guardrails and is exempt)
    final var upgradeChangesets =
        Arrays.stream(
                new PathMatchingResourcePatternResolver()
                    .getResources("classpath*:" + CHANGESET_PATH + "*.xml"))
            .filter(resource -> !Objects.equals(resource.getFilename(), "8.9.0.xml"))
            // sorting as version numbers so e.g. 8.9.9 will appear before 8.10.0 (instead of
            // lexical sorting, which would have 8.10.0 before 8.9.9)
            .sorted(Comparator.comparing(LiquibaseScriptGenerator::versionNumberFromResource))
            .map(it -> CHANGESET_PATH + it.getFilename())
            .distinct()
            .toList();

    for (final var databaseVersion : DATABASE_VERSIONS) {
      // We generate create scripts for the latest version from the changelog-master.xml
      generateLiquibaseScript(
          databaseVersion,
          CHANGELOG_PATH + "changelog-master.xml",
          prefix,
          targetDir + "/create",
          databaseVersion.vendor() + "_master.sql");

      // We generate upgrade scripts for each version (except 8.9.0) from the changesets, so that
      // customers, who created their schema with an older version can upgrade to the latest
      // version step by step

      // 8.9.0 is the initial version of the RDBMS schema, so we start upgrades from there
      String previousVersion = "8.9.0";

      for (final var changesetFile : upgradeChangesets) {
        final String fileName = Paths.get(changesetFile).getFileName().toString();
        final String sqlOutputDir = targetDir + "/upgrade";
        generateLiquibaseScript(
            databaseVersion,
            changesetFile,
            prefix,
            sqlOutputDir,
            String.format(
                "%s_upgrade_%s_to_%s.sql",
                databaseVersion.vendor(), previousVersion, fileName.replace(".xml", "")));

        previousVersion = fileName.replace(".xml", "");
      }
    }
  }

  public static void generateLiquibaseScript(
      final DatabaseVersion databaseVersion,
      final String changesetFile,
      final String prefix,
      final String targetBaseDir,
      final String outputFileName)
      throws Exception {
    final var properties = VendorDatabasePropertiesLoader.load(databaseVersion.vendor());

    final var sqlScript =
        generateSqlScript(
            databaseVersion,
            changesetFile,
            prefix,
            properties.userCharColumnSize(),
            properties.errorMessageSize(),
            properties.treePathSize());

    final String basedir = targetBaseDir + "/" + databaseVersion.vendor();
    Files.createDirectories(Paths.get(basedir));
    Files.write(
        Paths.get(basedir + "/" + outputFileName),
        sqlScript.getBytes(),
        StandardOpenOption.CREATE,
        StandardOpenOption.TRUNCATE_EXISTING);
  }

  public static String generateSqlScript(
      final DatabaseVersion databaseVersion,
      final String changesetFile,
      final String prefix,
      final int userCharColumnSize,
      final int errorMessageSize,
      final int treePathSize)
      throws LiquibaseException {

    final Database database;
    if (databaseVersion.targetVersion() != null) {
      final var resourceAccessor = new ClassLoaderResourceAccessor();
      final var conn =
          new OfflineConnection(
              "offline:" + databaseVersion.vendor() + "?version=" + databaseVersion.targetVersion(),
              resourceAccessor);
      database = DatabaseFactory.getInstance().findCorrectDatabaseImplementation(conn);
    } else {
      database = DatabaseFactory.getInstance().getDatabase(databaseVersion.vendor());
    }

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

        sqlScript.append(sqlString);
        sqlScript.append(";");
        sqlScript.append("\n");
      }

      sqlScript.append("\n");
    }
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

  public static DatabaseVersion getDatabaseVersion(final String databaseId) {
    return Arrays.stream(LiquibaseScriptGenerator.DATABASE_VERSIONS)
        .filter(dv -> dv.vendor().equals(databaseId))
        .findFirst()
        .orElse(new DatabaseVersion(databaseId));
  }

  private static VersionNumber versionNumberFromResource(final Resource resource) {
    final var filename = resource.getFilename();
    if (filename != null) {
      return parseVersionNumber(filename.replace(".xml", ""));
    }
    throw new IllegalArgumentException(
        "Expected resource with filename in format 'major.minor.patch.xml', but got: " + resource);
  }

  private static VersionNumber parseVersionNumber(final String str) {
    final var parts = str.split("\\.");
    if (parts.length == 3) {
      try {
        return new VersionNumber(
            Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), Integer.parseInt(parts[2]));
      } catch (final NumberFormatException e) {
        // fall through to exception below
      }
    }
    throw new IllegalArgumentException(
        "Expected version number in format 'major.minor.patch', but got: " + str);
  }

  /**
   * Pairs a database vendor name with an optional target version for SQL generation.
   *
   * <p>When {@code targetVersion} is non-null, an {@link OfflineConnection} is used so that
   * Liquibase applies type mappings for that specific version rather than defaulting to the latest
   * (e.g. Oracle 19 emits {@code NUMBER(1,0)} for boolean columns instead of {@code BOOLEAN}).
   */
  public record DatabaseVersion(String vendor, Long targetVersion) {
    public DatabaseVersion(final String vendor) {
      this(vendor, null);
    }
  }

  record VersionNumber(int major, int minor, int patch) implements Comparable<VersionNumber> {
    @Override
    public int compareTo(final VersionNumber other) {
      if (major != other.major) {
        return Integer.compare(major, other.major);
      }
      if (minor != other.minor) {
        return Integer.compare(minor, other.minor);
      }
      return Integer.compare(patch, other.patch);
    }
  }
}
