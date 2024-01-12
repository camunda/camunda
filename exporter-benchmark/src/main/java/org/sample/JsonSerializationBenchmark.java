/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package org.sample;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.Warmup;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.operate.entities.listview.ProcessInstanceForListViewEntity;
import io.camunda.operate.json.ProcessInstanceForListViewEntityWriter;

public class JsonSerializationBenchmark {

  @Benchmark
  @BenchmarkMode(Mode.Throughput)
  @Warmup(iterations = 1, time = 15, timeUnit = TimeUnit.SECONDS)
  @Measurement(iterations = 3, time = 30, timeUnit = TimeUnit.SECONDS)
  @Fork(value = 1, warmups = 1)
  public void testGeneratorSerialization(JsonSerializationState state) throws IOException {
    ProcessInstanceForListViewEntity entity = state.getEntityFactory().buildProcessInstanceForListViewEntity();
    
    ByteArrayOutputStream outStream = new ByteArrayOutputStream();
    JsonGenerator jsonGenerator = state.getObjectMapper().createGenerator(outStream);
    
    ProcessInstanceForListViewEntityWriter writer = new ProcessInstanceForListViewEntityWriter();
    writer.writeTo(entity, jsonGenerator);
    
    jsonGenerator.flush();
    jsonGenerator.close();
    
    outStream.toByteArray();
    
  }

  @Benchmark
  @BenchmarkMode(Mode.Throughput)
  @Warmup(iterations = 1, time = 5, timeUnit = TimeUnit.SECONDS)
  @Measurement(iterations = 3, time = 30, timeUnit = TimeUnit.SECONDS)
  @Fork(value = 1, warmups = 1)
  public void testMapperSerialization(JsonSerializationState state) throws IOException {
    ProcessInstanceForListViewEntity entity = state.getEntityFactory().buildProcessInstanceForListViewEntity();
    
    ByteArrayOutputStream outStream = new ByteArrayOutputStream();
    ObjectMapper objectMapper = state.getObjectMapper();

    objectMapper.writeValue(outStream, entity);
    
    outStream.toByteArray();
  }
}
