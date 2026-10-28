/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.physicaltenant.cost;

import java.lang.management.ManagementFactory;
import java.time.Duration;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.management.JMException;
import javax.management.ObjectName;
import org.awaitility.Awaitility;
import org.jspecify.annotations.NullMarked;

/** The live heap, thread count and class histogram of this JVM after a stabilised full GC. */
@NullMarked
record JvmFootprint(long heapUsedBytes, int threads, Map<String, ClassUsage> histogram) {

  private static final Pattern HISTOGRAM_LINE =
      Pattern.compile("^\\s*\\d+:\\s+(\\d+)\\s+(\\d+)\\s+(\\S+).*$");

  static JvmFootprint capture() {
    final var previous = new AtomicLong(Long.MAX_VALUE);
    Awaitility.await("heap used is stable across full GCs")
        .atMost(Duration.ofMinutes(2))
        .pollInterval(Duration.ofSeconds(1))
        .until(
            () -> {
              ManagementFactory.getMemoryMXBean().gc();
              final long used = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed();
              final long before = previous.getAndSet(used);
              return Math.abs(before - used) <= used / 100;
            });
    final var histogram = classHistogram();
    return new JvmFootprint(
        ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed(),
        ManagementFactory.getThreadMXBean().getThreadCount(),
        histogram);
  }

  JvmFootprint minus(final JvmFootprint baseline) {
    final var delta = new HashMap<String, ClassUsage>();
    histogram.forEach(
        (name, usage) ->
            delta.put(name, usage.minus(baseline.histogram.getOrDefault(name, ClassUsage.NONE))));
    return new JvmFootprint(
        heapUsedBytes - baseline.heapUsedBytes, threads - baseline.threads, delta);
  }

  long instances(final String className) {
    return histogram.getOrDefault(className, ClassUsage.NONE).instances();
  }

  List<Map.Entry<String, ClassUsage>> topByBytes(final int limit) {
    return histogram.entrySet().stream()
        .sorted(Comparator.comparingLong(e -> -e.getValue().bytes()))
        .limit(limit)
        .toList();
  }

  private static Map<String, ClassUsage> classHistogram() {
    final String text;
    try {
      text =
          (String)
              ManagementFactory.getPlatformMBeanServer()
                  .invoke(
                      new ObjectName("com.sun.management:type=DiagnosticCommand"),
                      "gcClassHistogram",
                      new Object[] {null},
                      new String[] {String[].class.getName()});
    } catch (final JMException e) {
      throw new IllegalStateException("Failed to read the class histogram", e);
    }
    final var histogram = new HashMap<String, ClassUsage>();
    text.lines()
        .map(HISTOGRAM_LINE::matcher)
        .filter(Matcher::matches)
        .forEach(
            m ->
                histogram.merge(
                    m.group(3),
                    new ClassUsage(Long.parseLong(m.group(1)), Long.parseLong(m.group(2))),
                    ClassUsage::plus));
    return histogram;
  }

  record ClassUsage(long instances, long bytes) {
    static final ClassUsage NONE = new ClassUsage(0, 0);

    ClassUsage minus(final ClassUsage other) {
      return new ClassUsage(instances - other.instances, bytes - other.bytes);
    }

    ClassUsage plus(final ClassUsage other) {
      return new ClassUsage(instances + other.instances, bytes + other.bytes);
    }
  }
}
