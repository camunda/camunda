/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.c7bench.loadtester.starter;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Deploys the benchmark BPMN via the REST API, retrying until the engine is reachable. */
final class ProcessDeployer {

  private static final Logger LOG = LoggerFactory.getLogger(ProcessDeployer.class);

  private ProcessDeployer() {}

  static void deploy(
      final HttpClient http, final String restUrl, final String fileName, final String bpmnXml) {
    final String boundary = "----c7bench" + UUID.randomUUID();
    final byte[] body = multipart(boundary, fileName, bpmnXml);
    final HttpRequest request =
        HttpRequest.newBuilder(URI.create(restUrl + "/deployment/create"))
            .timeout(Duration.ofSeconds(30))
            .header("Content-Type", "multipart/form-data; boundary=" + boundary)
            .POST(HttpRequest.BodyPublishers.ofByteArray(body))
            .build();

    while (true) {
      try {
        final HttpResponse<String> response =
            http.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() == 200) {
          LOG.info("Deployed {}", fileName);
          return;
        }
        LOG.warn("Deployment failed: HTTP {} {}", response.statusCode(), response.body());
      } catch (final Exception e) {
        LOG.warn("Engine not reachable yet at {}: {}", restUrl, e.toString());
      }
      sleep();
    }
  }

  private static byte[] multipart(
      final String boundary, final String fileName, final String bpmnXml) {
    final ByteArrayOutputStream out = new ByteArrayOutputStream();
    field(out, boundary, "deployment-name", "benchmark");
    field(out, boundary, "enable-duplicate-filtering", "true");
    field(out, boundary, "deployment-source", "c7-load-tester");
    write(
        out,
        "--"
            + boundary
            + "\r\nContent-Disposition: form-data; name=\"data\"; filename=\""
            + fileName
            + "\"\r\nContent-Type: application/octet-stream\r\n\r\n"
            + bpmnXml
            + "\r\n--"
            + boundary
            + "--\r\n");
    return out.toByteArray();
  }

  private static void field(
      final ByteArrayOutputStream out, final String boundary, final String name, final String v) {
    write(
        out,
        "--" + boundary + "\r\nContent-Disposition: form-data; name=\"" + name + "\"\r\n\r\n" + v
            + "\r\n");
  }

  private static void write(final ByteArrayOutputStream out, final String s) {
    out.writeBytes(s.getBytes(StandardCharsets.UTF_8));
  }

  private static void sleep() {
    try {
      Thread.sleep(2000);
    } catch (final InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(e);
    }
  }
}
