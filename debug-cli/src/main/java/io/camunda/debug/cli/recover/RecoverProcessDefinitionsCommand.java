/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.debug.cli.recover;

import static io.camunda.debug.cli.util.ErrorMessageUtil.rootMessage;

import io.camunda.debug.cli.recover.ProcessDefinitionRecovery.Summary;
import io.camunda.debug.cli.state.SnapshotUtil;
import io.camunda.exporter.adapters.ClientAdapter;
import io.camunda.exporter.handlers.EmbeddedFormHandler;
import io.camunda.exporter.handlers.ProcessHandler;
import io.camunda.search.connect.configuration.ConnectConfiguration;
import io.camunda.search.connect.configuration.DatabaseType;
import io.camunda.webapps.schema.descriptors.IndexDescriptors;
import io.camunda.webapps.schema.descriptors.index.FormIndex;
import io.camunda.webapps.schema.descriptors.index.ProcessIndex;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.engine.EngineConfiguration;
import io.camunda.zeebe.engine.state.deployment.DbProcessState;
import io.camunda.zeebe.protocol.ZbColumnFamilies;
import io.camunda.zeebe.snapshots.impl.FileBasedSnapshotStoreImpl;
import io.camunda.zeebe.util.FileUtil;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.InstantSource;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

/**
 * Re-exports process definitions from a Zeebe partition-1 snapshot (primary storage) into secondary
 * storage (ES/OS). Partition 1 is the deployment partition, so its process state is a complete
 * superset of all deployments across all tenants, and every process-definition key is the id of the
 * corresponding secondary-storage document.
 *
 * <p>The snapshot is read strictly read-only (copied into a throwaway runtime first), so this can
 * be run in-pod on a live broker hosting a partition-1 replica — no outage required.
 *
 * <p>Output convention: stdout carries the machine-readable summary line; human-readable progress
 * goes to stderr.
 */
@Command(
    name = "process-definitions",
    description =
        "Re-export ACTIVE process definitions (and their embedded start forms) from a partition-1 "
            + "snapshot into secondary storage (Elasticsearch/OpenSearch).")
public class RecoverProcessDefinitionsCommand implements Callable<Integer> {

  @Spec private CommandSpec spec;

  // --- Primary-storage (read) options, mirroring the `state` commands ---

  @Option(
      names = {"-r", "--root"},
      description =
          "Path of the partition-1 directory (the folder containing 'snapshots/'), e.g. "
              + "<data>/raft-partition/partitions/1",
      required = true)
  private Path root;

  @Option(
      names = {"-s", "--snapshot"},
      description = "Id of the snapshot directory to read (under '<root>/snapshots/')",
      required = true)
  private String snapshotId;

  @Option(
      names = {"--runtime"},
      description =
          "Path to a temporary runtime directory the snapshot is copied into before reading. "
              + "A fresh temp directory is created and deleted automatically if omitted.")
  private Path runtimePath;

  // --- Secondary-storage (write) connection options, defaulted from the container env ---

  @Option(
      names = {"--connect-type"},
      description = "Secondary storage type: elasticsearch|opensearch (default: ${DEFAULT-VALUE})",
      defaultValue = "${env:CAMUNDA_DATABASE_TYPE:-elasticsearch}")
  private String connectType;

  @Option(
      names = {"--connect-url"},
      description = "Secondary storage URL (default: ${DEFAULT-VALUE})",
      defaultValue = "${env:CAMUNDA_DATABASE_URL:-http://localhost:9200}")
  private String connectUrl;

  @Option(
      names = {"--connect-username"},
      description = "Secondary storage username",
      defaultValue = "${env:CAMUNDA_DATABASE_USERNAME}")
  private String connectUsername;

  @Option(
      names = {"--connect-password"},
      description = "Secondary storage password",
      defaultValue = "${env:CAMUNDA_DATABASE_PASSWORD}")
  private String connectPassword;

  @Option(
      names = {"--index-prefix"},
      description =
          "Index prefix of the target secondary storage. MUST match the running installation; a "
              + "wrong prefix targets a non-existent index and the command fails fast.",
      defaultValue = "${env:CAMUNDA_DATABASE_INDEXPREFIX}")
  private String indexPrefix;

  @Option(
      names = {"--connect-security-enabled"},
      description = "Enable TLS to the secondary storage",
      defaultValue = "${env:CAMUNDA_DATABASE_SECURITY_ENABLED:-false}")
  private boolean securityEnabled;

  @Option(
      names = {"--connect-security-certificate-path"},
      description = "Path to the CA certificate used for TLS",
      defaultValue = "${env:CAMUNDA_DATABASE_SECURITY_CERTIFICATEPATH}")
  private String certificatePath;

  @Option(
      names = {"--connect-security-verify-hostname"},
      description = "Verify the TLS hostname (default: ${DEFAULT-VALUE})",
      defaultValue = "${env:CAMUNDA_DATABASE_SECURITY_VERIFYHOSTNAME:-true}")
  private boolean verifyHostname;

  @Option(
      names = {"--connect-security-self-signed"},
      description = "Accept a self-signed TLS certificate (default: ${DEFAULT-VALUE})",
      defaultValue = "${env:CAMUNDA_DATABASE_SECURITY_SELFSIGNED:-false}")
  private boolean selfSigned;

  // --- Behavior options ---

  @Option(
      names = {"--override"},
      description =
          "Rewrite process definitions that already exist in secondary storage (default: only "
              + "write missing ones).")
  private boolean override;

  @Option(
      names = {"--dry-run"},
      description = "Compute and report the diff without writing anything.")
  private boolean dryRun;

  @Option(
      names = {"--batch-size"},
      description =
          "Number of process definitions to write per bulk request (default: ${DEFAULT-VALUE}).",
      defaultValue = "50")
  private int batchSize;

  @Override
  public Integer call() throws Exception {
    final PrintWriter out = spec.commandLine().getOut();
    final PrintWriter err = spec.commandLine().getErr();

    if (batchSize <= 0) {
      err.println("--batch-size must be greater than 0");
      return 1;
    }

    final ConnectConfiguration connectConfiguration = buildConnectConfiguration(err);
    if (connectConfiguration == null) {
      return 1;
    }
    final DatabaseType databaseType = connectConfiguration.getTypeEnum();
    final boolean isElasticsearch = databaseType.isElasticSearch();

    final var indexDescriptors =
        new IndexDescriptors(connectConfiguration.getIndexPrefix(), isElasticsearch);
    final String processIndexName = indexDescriptors.get(ProcessIndex.class).getFullQualifiedName();
    final String formIndexName = indexDescriptors.get(FormIndex.class).getFullQualifiedName();

    final Path snapshotPath =
        root.resolve(FileBasedSnapshotStoreImpl.SNAPSHOTS_DIRECTORY).resolve(snapshotId);
    if (!Files.isDirectory(snapshotPath)) {
      err.println("Snapshot directory does not exist: " + snapshotPath);
      return 1;
    }

    final Path runtime;
    final Path ephemeralParent;
    if (runtimePath == null) {
      try {
        ephemeralParent = Files.createTempDirectory("cdbg-recover-");
      } catch (final IOException e) {
        err.println(
            "Failed to create a temporary runtime directory under "
                + System.getProperty("java.io.tmpdir")
                + ": "
                + rootMessage(e)
                + ". Retry with --runtime pointing at a directory the current user can write to.");
        return 1;
      }
      runtime = ephemeralParent.resolve("runtime");
    } else {
      ephemeralParent = null;
      runtime = runtimePath;
    }

    err.println("=== Recovering process definitions ===");
    err.println("Reading snapshot: " + snapshotPath);
    err.println(
        "Target: "
            + databaseType
            + " at "
            + connectConfiguration.getUrl()
            + " (index prefix '"
            + connectConfiguration.getIndexPrefix()
            + "')");
    err.println(
        "Mode: "
            + (dryRun ? "dry-run" : (override ? "override" : "missing-only"))
            + ", batch size "
            + batchSize);

    try (final ClientAdapter clientAdapter = ClientAdapter.of(connectConfiguration)) {
      final var searchEngineClient = clientAdapter.getSearchEngineClient();

      // Fail fast on a wrong --index-prefix: without the ProcessIndex, writes would silently target
      // a non-existent/mis-prefixed index instead of recovering into the real installation.
      if (!searchEngineClient.indexExists(processIndexName)) {
        err.println(
            "Process index '"
                + processIndexName
                + "' does not exist. Check --index-prefix / --connect-type matches the target "
                + "installation.");
        return 1;
      }
      if (!searchEngineClient.indexExists(formIndexName)) {
        err.println(
            "Form index '"
                + formIndexName
                + "' does not exist. Check --index-prefix / --connect-type matches the target "
                + "installation.");
        return 1;
      }

      final var processHandler = new ProcessHandler(processIndexName, new NoopProcessCache());
      final var embeddedFormHandler = new EmbeddedFormHandler(formIndexName);

      final var recovery =
          new ProcessDefinitionRecovery(
              processHandler,
              embeddedFormHandler,
              clientAdapter::createBatchRequest,
              key -> searchEngineClient.getDocument(processIndexName, String.valueOf(key)) != null,
              override,
              dryRun,
              batchSize,
              err);

      try (final ZeebeDb<ZbColumnFamilies> db = openReadOnly(snapshotPath, runtime)) {
        final var processState = openProcessState(db);
        final Summary summary =
            recovery.run(
                consumer ->
                    processState.forEachProcess(
                        null,
                        process -> {
                          consumer.accept(process);
                          return true;
                        }));

        printSummary(err, out, summary);
        return summary.hasFailures() ? 2 : 0;
      }
    } finally {
      if (ephemeralParent != null) {
        FileUtil.deleteFolderIfExists(ephemeralParent);
      }
    }
  }

  private ConnectConfiguration buildConnectConfiguration(final PrintWriter err) {
    final String normalizedType = normalizeType(connectType);
    if (normalizedType == null) {
      err.println(
          "Unsupported --connect-type '" + connectType + "'. Supported: elasticsearch|opensearch.");
      return null;
    }

    final var configuration = new ConnectConfiguration();
    configuration.setType(normalizedType);
    configuration.setUrl(connectUrl);
    if (isNotBlank(connectUsername)) {
      configuration.setUsername(connectUsername);
    }
    if (isNotBlank(connectPassword)) {
      configuration.setPassword(connectPassword);
    }
    if (indexPrefix != null) {
      configuration.setIndexPrefix(indexPrefix);
    }

    final var security = configuration.getSecurity();
    security.setEnabled(securityEnabled);
    security.setVerifyHostname(verifyHostname);
    security.setSelfSigned(selfSigned);
    if (isNotBlank(certificatePath)) {
      security.setCertificatePath(certificatePath);
    }
    return configuration;
  }

  private static String normalizeType(final String type) {
    if (type == null) {
      return null;
    }
    return switch (type.trim().toLowerCase()) {
      case "elasticsearch" -> DatabaseType.ELASTICSEARCH.toString();
      case "opensearch" -> DatabaseType.OPENSEARCH.toString();
      default -> null;
    };
  }

  @SuppressWarnings("unchecked")
  private static ZeebeDb<ZbColumnFamilies> openReadOnly(
      final Path snapshotPath, final Path runtime) {
    return (ZeebeDb<ZbColumnFamilies>) new SnapshotUtil().openSnapshot(snapshotPath, runtime);
  }

  private static DbProcessState openProcessState(final ZeebeDb<ZbColumnFamilies> db) {
    return new DbProcessState(
        db, db.createContext(), new EngineConfiguration(), InstantSource.fixed(Instant.EPOCH));
  }

  private void printSummary(final PrintWriter err, final PrintWriter out, final Summary summary) {
    err.println("=== Done ===");
    err.println("Active process definitions in snapshot: " + summary.total());
    err.println("Already present in secondary storage:   " + summary.alreadyPresent());
    err.println(
        (dryRun ? "Would write:" : "Written:")
            + "                            "
            + summary.written());
    err.println("Skipped (not ACTIVE):                   " + summary.skippedInactive());
    err.println("Failed:                                 " + summary.failed());
    // Machine-readable summary on stdout.
    out.printf(
        "total=%d present=%d written=%d skipped=%d failed=%d%n",
        summary.total(),
        summary.alreadyPresent(),
        summary.written(),
        summary.skippedInactive(),
        summary.failed());
  }

  private static boolean isNotBlank(final String value) {
    return value != null && !value.isBlank();
  }
}
