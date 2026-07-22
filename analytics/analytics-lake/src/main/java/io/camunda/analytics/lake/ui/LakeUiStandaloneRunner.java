/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.lake.ui;

import java.nio.file.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * One-shot CLI entry point: starts only {@link LakeUiServer} against an <b>existing</b> lake
 * warehouse/state directory -- no Event Bridge connection, no {@link
 * io.camunda.analytics.lake.write.IcebergLakeWriter}, no translator process required.
 *
 * <p>Mirrors {@code io.camunda.analytics.lake.write.GoldTablesStandaloneRunner}'s own rationale:
 * this server already opens its own independent {@code JdbcCatalog}/DuckDB connection (see {@link
 * LakeUiServer}'s class javadoc), so browsing a preserved demo warehouse -- e.g. one a translator
 * process is no longer running against -- needs nothing more than this class to spin up.
 *
 * <h2>Usage</h2>
 *
 * <pre>
 *   java -cp ... io.camunda.analytics.lake.ui.LakeUiStandaloneRunner [warehouseDir] [stateDir] [port] [bpmnDir]
 * </pre>
 *
 * <p>All arguments are optional and positional; {@code warehouseDir} defaults to {@code
 * ./data/lake} (same default {@code lake.dir} uses), {@code stateDir} to {@code ./data/lake-state},
 * {@code port} to {@code 8091} (same default {@code lake.uiPort} uses), and {@code bpmnDir} to
 * {@code null} (use {@link BpmnCatalog}'s default resolution). Runs until interrupted ({@code
 * Ctrl-C}); the shutdown hook closes the server cleanly.
 */
public final class LakeUiStandaloneRunner {

  private static final Logger LOG = LoggerFactory.getLogger(LakeUiStandaloneRunner.class);

  private LakeUiStandaloneRunner() {}

  public static void main(final String[] args) throws InterruptedException {
    final Path warehouseDir =
        args.length > 0 ? Path.of(args[0]) : Path.of(System.getProperty("lake.dir", "./data/lake"));
    final Path stateDir = args.length > 1 ? Path.of(args[1]) : Path.of("./data/lake-state");
    final int port = args.length > 2 ? Integer.parseInt(args[2]) : 8091;
    final Path bpmnDir = args.length > 3 ? Path.of(args[3]) : null;

    LOG.info(
        "Starting lake UI standalone (read-only) -- warehouse={} state={} port={} bpmnDir={}",
        warehouseDir,
        stateDir,
        port,
        bpmnDir);

    final LakeUiServer server = new LakeUiServer(port, warehouseDir, stateDir, bpmnDir);
    server.start();
    LOG.info(
        "Lake UI: http://localhost:{}  (dashboard: /dashboard, process map: /process-map)", port);

    Runtime.getRuntime()
        .addShutdownHook(
            new Thread(
                () -> {
                  LOG.info("Shutdown requested; closing the UI server");
                  server.close();
                },
                "lake-ui-standalone-shutdown"));

    // Park the main thread -- the HTTP server runs on its own request-executor threads.
    Thread.currentThread().join();
  }
}
