/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.webapp;

import java.sql.SQLException;
import org.h2.tools.Server;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Minimal analytics webapp: a read/serving layer over the H2 dataset the analytics pipeline
 * produces, plus a static UI to declare a dataset, build a report on it, and view it.
 *
 * <p>For <b>live</b> data the pipeline and this webapp must share the dataset DB, and file H2 is
 * single-process — so set {@code analytics.dataset.server.port} to have the webapp host an H2 TCP
 * server over its data dir, then point both this app's datasource and the pipeline at {@code
 * jdbc:h2:tcp://localhost:<port>/analytics-dataset} (user {@code sa}). Without that property it
 * runs standalone against the configured (in-memory/file) datasource.
 */
@SpringBootApplication
public class AnalyticsWebappApplication {

  private static final Logger LOG = LoggerFactory.getLogger(AnalyticsWebappApplication.class);

  public static void main(final String[] args) throws SQLException {
    final String tcpPort = System.getProperty("analytics.dataset.server.port", "");
    if (!tcpPort.isBlank()) {
      final String baseDir = System.getProperty("analytics.dataset.server.dir", "./data");
      final Server h2 =
          Server.createTcpServer(
                  "-tcpPort", tcpPort, "-tcpAllowOthers", "-ifNotExists", "-baseDir", baseDir)
              .start();
      LOG.info("H2 TCP server started on {} (baseDir {})", h2.getURL(), baseDir);
    }
    SpringApplication.run(AnalyticsWebappApplication.class, args);
  }
}
