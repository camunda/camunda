/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package org.sample;

public class BenchmarkRunner {
  public static void main(String[] args) throws Exception {
    org.openjdk.jmh.Main.main(args);

    //    Options options = new OptionsBuilder().include(MyBenchmark.class.getSimpleName()).build();
    //    new Runner(options).run();
  }
}
