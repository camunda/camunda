/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.benchmark;

import java.util.*;

public class PrometheusParser {

  private static final java.util.regex.Pattern METRIC_LINE =
      java.util.regex.Pattern.compile(
          "^(?<name>[a-zA-Z_:][a-zA-Z0-9_:]*)(\\{(?<labels>[^}]*)})?\\s+(?<value>[-+]?[0-9]*\\.?[0-9]+([eE][-+]?[0-9]+)?)");

  public static List<PrometheusMetric> parse(final String prometheusText) {
    final List<PrometheusMetric> metrics = new ArrayList<>();

    for (final String line : prometheusText.split("\n")) {
      if (line.isEmpty() || line.startsWith("#")) {
        continue; // skip comments
      }

      final var matcher = METRIC_LINE.matcher(line);
      if (matcher.find()) {
        final String name = matcher.group("name");

        Map<String, String> labels = new HashMap<>();
        if (matcher.group("labels") != null) {
          labels = parseLabels(matcher.group("labels"));
        }

        final double value = Double.parseDouble(matcher.group("value"));

        metrics.add(new PrometheusMetric(name, labels, value));
      }
    }

    return metrics;
  }

  private static Map<String, String> parseLabels(final String raw) {
    final Map<String, String> labels = new HashMap<>();

    // label="value",label2="value2"
    final String[] parts = raw.split(",");
    for (final String part : parts) {
      final String[] kv = part.split("=", 2);
      if (kv.length == 2) {
        labels.put(kv[0], kv[1].replaceAll("^\"|\"$", "")); // remove quotes
      }
    }

    return labels;
  }
}
