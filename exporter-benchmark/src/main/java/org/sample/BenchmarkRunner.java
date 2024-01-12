/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package org.sample;

import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

public class BenchmarkRunner {
  public static void main(String[] args) throws Exception {
//    org.openjdk.jmh.Main.main(args);

      Options options = new OptionsBuilder().include(JsonSerializationBenchmark.class.getSimpleName()).build();
      new Runner(options).run();
  }
}
