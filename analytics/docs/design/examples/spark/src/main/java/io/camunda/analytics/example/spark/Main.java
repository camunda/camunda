/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.example.spark;

import java.sql.Timestamp;
import org.apache.spark.api.java.function.MapFunction;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Encoders;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.SparkSession;
import org.apache.spark.sql.streaming.StreamingQuery;

/**
 * Entry point for the reference Spark Structured Streaming analytics pipeline.
 *
 * <p>REFERENCE EXAMPLE — not built, not wired into anything. Dependencies will not resolve offline;
 * that is expected.
 *
 * <p>On Java 21 Spark needs the usual reflective-access flags, e.g.:
 *
 * <pre>
 *   --add-opens=java.base/java.lang=ALL-UNNAMED
 *   --add-opens=java.base/java.nio=ALL-UNNAMED
 *   --add-opens=java.base/sun.nio.ch=ALL-UNNAMED
 * </pre>
 *
 * <p>This {@code main} wires a <em>synthetic</em> source (Spark's built-in {@code rate} source,
 * mapped into {@link SourceEvent}s) purely so the DAG is self-contained and readable. The real
 * source would be the event bridge / Kafka — see the commented block below.
 */
public final class Main {

  private Main() {}

  public static void main(final String[] args) throws Exception {
    final SparkSession spark =
        SparkSession.builder()
            .appName("analytics-example-spark")
            .master("local[*]") // a real deployment omits this and is launched via spark-submit
            // Shuffle partitions default to 200; for a tiny local demo a handful is plenty. This is
            // the knob that controls the width of the Stage B/C groupBy exchange.
            .config("spark.sql.shuffle.partitions", "4")
            // The state store implementation. RocksDB keeps large keyed state off-heap and is the
            // production choice for arbitrary stateful processing; the default is an in-memory map.
            .config(
                "spark.sql.streaming.stateStore.providerClass",
                "org.apache.spark.sql.execution.streaming.state.RocksDBStateStoreProvider")
            .getOrCreate();

    final Dataset<SourceEvent> events = syntheticSource(spark);

    // ---- The real source would look like this (Kafka / event-bridge) -----------------------------
    // Dataset<SourceEvent> events = spark.readStream()
    //     .format("kafka")
    //     .option("kafka.bootstrap.servers", "...")
    //     .option("subscribe", "process-events")
    //     .option("startingOffsets", "earliest")   // offset log takes over after the first batch
    //     .load()
    //     .select(from_avro(col("value"), schema).as("e"))   // or from_protobuf / a UDF
    //     .select("e.*")
    //     .as(Encoders.bean(SourceEvent.class));
    // Kafka's per-partition offsets are what Spark records in its offset log, which is the backbone
    // of replay-based exactly-once (see README).
    // ----------------------------------------------------------------------------------------------

    final long activationOffset = 0L; // forward-only gate boundary (Stage B)
    final String checkpointLocation = "/tmp/analytics-example-spark/checkpoint";

    final StreamingQuery query =
        AnalyticsPipeline.start(events, activationOffset, checkpointLocation);

    AnalyticsPipeline.awaitTermination(query);
  }

  /**
   * Maps Spark's built-in {@code rate} source into a synthetic ACTIVATED→COMPLETED event stream so
   * the pipeline can run standalone. Even values start an instance; odd values complete the instance
   * started one tick earlier, yielding a ~1 tick duration.
   */
  private static Dataset<SourceEvent> syntheticSource(final SparkSession spark) {
    return spark
        .readStream()
        .format("rate")
        .option("rowsPerSecond", 10)
        .load() // columns: timestamp (Timestamp), value (long)
        .map(
            (MapFunction<Row, SourceEvent>)
                row -> {
                  final long value = row.getLong(row.fieldIndex("value"));
                  final Timestamp ts = row.getTimestamp(row.fieldIndex("timestamp"));
                  final boolean start = (value % 2 == 0);
                  final long instanceKey = start ? value : value - 1;

                  final SourceEvent e = new SourceEvent();
                  e.setType(
                      start ? SourceEvent.Type.ACTIVATED : SourceEvent.Type.COMPLETED);
                  e.setInstanceKey(instanceKey);
                  e.setProcessId("order-process");
                  e.setTenantId("<default>");
                  e.setTimestampMs(ts.getTime());
                  e.setEventTime(ts);
                  e.setSourcePartition(0);
                  e.setSourceOffset(value);
                  return e;
                },
            Encoders.bean(SourceEvent.class));
  }
}
