/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.physicaltenant.cost;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.ToDoubleFunction;
import org.jspecify.annotations.NullMarked;

/**
 * Collects one {@link Sample} per tenant count and phase, and fits each cost against the tenant
 * count by least squares, so the per-tenant cost is the slope.
 */
@NullMarked
final class CostReport {

  private static final String CSV_HEADER =
      "label,brokers,tenants,phase,startupMillis,heapBytes,threads,connections,hikariPools,"
          + "myBatisConfigurations,mappedStatements";

  private final String label;
  private final Path directory;
  private final List<Sample> samples = new ArrayList<>();

  CostReport(final String label, final Path directory) {
    this.label = label;
    this.directory = directory;
  }

  synchronized void add(final Sample sample) throws IOException {
    samples.add(sample);
    Files.createDirectories(directory);
    final var csv = directory.resolve("pt-cost-" + label + ".csv");
    if (!Files.exists(csv)) {
      Files.writeString(csv, CSV_HEADER + System.lineSeparator());
    }
    Files.writeString(csv, sample.toCsv(label) + System.lineSeparator(), StandardOpenOption.APPEND);
  }

  synchronized void writeSummary() throws IOException {
    final var out = new StringBuilder();
    out.append("# Physical tenant cost: ").append(label).append("\n\n");
    out.append(
        "| phase | brokers | tenants | startup s | heap MiB | threads | connections | Hikari pools"
            + " | MyBatis configurations | mapped statements |\n");
    out.append("|---|---|---|---|---|---|---|---|---|---|\n");
    samples.forEach(s -> out.append(s.toMarkdownRow()).append('\n'));
    out.append("\n## Fitted cost per tenant (least squares over the tenant count)\n\n");
    out.append(
        "| phase | heap KiB/tenant | threads/tenant | connections/tenant | startup ms/tenant |\n");
    out.append("|---|---|---|---|---|\n");
    samples.stream()
        .map(Sample::phase)
        .distinct()
        .forEach(
            phase -> {
              final var ofPhase = samples.stream().filter(s -> s.phase().equals(phase)).toList();
              out.append(
                  String.format(
                      Locale.ROOT,
                      "| %s | %s | %s | %s | %s |%n",
                      phase,
                      fit(ofPhase, s -> s.heapBytes() / 1024.0),
                      fit(ofPhase, Sample::threads),
                      fit(ofPhase, Sample::connections),
                      fit(ofPhase, Sample::startupMillis)));
            });
    out.append("\n## Largest growth by class (shallow bytes, whole JVM)\n");
    samples.forEach(
        sample -> {
          out.append(
              "\n### %s, %d tenants\n\n| class | instances | bytes |\n|---|---|---|\n"
                  .formatted(sample.phase(), sample.tenants()));
          sample.topClasses().forEach(row -> out.append(row).append('\n'));
        });
    Files.createDirectories(directory);
    Files.writeString(directory.resolve("pt-cost-" + label + ".md"), out);
  }

  private static String fit(final List<Sample> samples, final ToDoubleFunction<Sample> metric) {
    if (samples.size() < 2) {
      return "n/a";
    }
    final double n = samples.size();
    final double meanX = samples.stream().mapToDouble(Sample::tenants).sum() / n;
    final double meanY = samples.stream().mapToDouble(metric).sum() / n;
    double sxy = 0;
    double sxx = 0;
    double syy = 0;
    for (final var s : samples) {
      final double dx = s.tenants() - meanX;
      final double dy = metric.applyAsDouble(s) - meanY;
      sxy += dx * dy;
      sxx += dx * dx;
      syy += dy * dy;
    }
    final double slope = sxy / sxx;
    final double intercept = meanY - slope * meanX;
    final double r2 = syy == 0 ? 1 : (sxy * sxy) / (sxx * syy);
    return String.format(Locale.ROOT, "%.1f (+%.0f, R²=%.3f)", slope, intercept, r2);
  }

  record Sample(
      String phase,
      int brokers,
      int tenants,
      long startupMillis,
      long heapBytes,
      int threads,
      long connections,
      long hikariPools,
      long myBatisConfigurations,
      long mappedStatements,
      List<String> topClasses) {

    String toCsv(final String label) {
      return String.join(
          ",",
          label,
          Integer.toString(brokers),
          Integer.toString(tenants),
          phase,
          Long.toString(startupMillis),
          Long.toString(heapBytes),
          Integer.toString(threads),
          Long.toString(connections),
          Long.toString(hikariPools),
          Long.toString(myBatisConfigurations),
          Long.toString(mappedStatements));
    }

    String toMarkdownRow() {
      return String.format(
          Locale.ROOT,
          "| %s | %d | %d | %.1f | %.1f | %d | %d | %d | %d | %d |",
          phase,
          brokers,
          tenants,
          startupMillis / 1000.0,
          heapBytes / (1024.0 * 1024.0),
          threads,
          connections,
          hikariPools,
          myBatisConfigurations,
          mappedStatements);
    }
  }
}
