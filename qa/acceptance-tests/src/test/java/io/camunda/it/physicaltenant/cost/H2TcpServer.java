/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.physicaltenant.cost;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.awaitility.Awaitility;
import org.h2.Driver;
import org.jspecify.annotations.NullMarked;

/**
 * An H2 TCP server in a child JVM, so that the in-memory databases of the tenants do not count
 * towards the heap of the node under measurement.
 */
@NullMarked
final class H2TcpServer implements AutoCloseable {

  private final Process process;
  private final int port;

  private H2TcpServer(final Process process, final int port) {
    this.process = process;
    this.port = port;
  }

  static H2TcpServer start(final Path logFile) throws IOException {
    final int port = freePort();
    final var process =
        new ProcessBuilder(
                List.of(
                    ProcessHandle.current().info().command().orElse("java"),
                    "-Xmx2g",
                    "-cp",
                    h2Jar(),
                    "org.h2.tools.Server",
                    "-tcp",
                    "-tcpPort",
                    Integer.toString(port),
                    "-ifNotExists"))
            .redirectErrorStream(true)
            .redirectOutput(logFile.toFile())
            .start();
    final var server = new H2TcpServer(process, port);
    try {
      Awaitility.await("H2 TCP server accepts connections on port " + port)
          .atMost(Duration.ofSeconds(30))
          .ignoreExceptions()
          .until(server::accepts);
    } catch (final RuntimeException e) {
      server.close();
      throw e;
    }
    return server;
  }

  String newDatabaseUrl(final String name) {
    return "jdbc:h2:tcp://localhost:%d/mem:%s-%s;DB_CLOSE_DELAY=-1;MODE=PostgreSQL"
        .formatted(port, name, UUID.randomUUID());
  }

  /** The sessions open on the database, as the server counts them, apart from this query's own. */
  long openSessions(final String url) throws SQLException {
    try (final var connection = DriverManager.getConnection(url, "sa", "");
        final var result =
            connection
                .createStatement()
                .executeQuery("SELECT COUNT(*) FROM INFORMATION_SCHEMA.SESSIONS")) {
      result.next();
      return result.getLong(1) - 1;
    }
  }

  @Override
  public void close() {
    process.destroy();
    try {
      if (!process.waitFor(10, TimeUnit.SECONDS)) {
        process.destroyForcibly();
      }
    } catch (final InterruptedException e) {
      Thread.currentThread().interrupt();
      process.destroyForcibly();
    }
  }

  private boolean accepts() throws IOException {
    try (final var ignored = new Socket("localhost", port)) {
      return true;
    }
  }

  private static int freePort() throws IOException {
    try (final var socket = new ServerSocket(0)) {
      return socket.getLocalPort();
    }
  }

  private static String h2Jar() {
    try {
      return Path.of(Driver.class.getProtectionDomain().getCodeSource().getLocation().toURI())
          .toString();
    } catch (final URISyntaxException e) {
      throw new IllegalStateException(e);
    }
  }
}
