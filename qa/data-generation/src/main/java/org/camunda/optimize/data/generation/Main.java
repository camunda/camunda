/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.data.generation;

import io.github.classgraph.ClassGraph;
import io.github.classgraph.ClassInfoList;
import io.github.classgraph.ScanResult;
import org.camunda.optimize.data.generation.generators.DataGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;


public class Main {

  private final Logger logger = LoggerFactory.getLogger(getClass());

  private long numberOfProcessInstancesToGenerate;
  private String engineRestEndpoint;
  private boolean removeDeployments;
  private HashMap<String, Integer> definitions;

  private Main(long numberOfProcessInstancesToGenerate,
               String engineRestEndpoint,
               boolean removeDeployments,
               HashMap<String, Integer> definitions) {
    this.numberOfProcessInstancesToGenerate = numberOfProcessInstancesToGenerate;
    this.engineRestEndpoint = engineRestEndpoint;
    this.removeDeployments = removeDeployments;
    this.definitions = definitions;
  }

  public static void main(String[] args) {
    // by default create 100 000 process instances
    Map<String, String> arguments = extractArguments(args);
    long numberOfProcessInstancesToGenerate =
      Long.parseLong(arguments.get("numberOfProcessInstances"));
    String engineRestEndpoint = arguments.get("engineRest");
    boolean removeDeployments = Boolean.parseBoolean(arguments.get("removeDeployments"));
    HashMap<String, Integer> definitions = parseDefinitions(arguments.get("definitions"));
    Main main = new Main(numberOfProcessInstancesToGenerate, engineRestEndpoint, removeDeployments, definitions);
    main.generateData();
  }

  public static HashMap<String, Integer> parseDefinitions(String definitions) {
    HashMap<String, Integer> res = new HashMap<>();
    String[] defs = definitions.split(",");
    for (String def : defs) {
      String[] strings = def.split(":");
      String key = strings[0] + "DataGenerator";
      if (strings.length == 1) {
        res.put(key, null);
      } else {
        res.put(key, Integer.parseInt(strings[1]));
      }
    }
    return res;
  }

  private static Map<String, String> extractArguments(String[] args) {
    if (arrayHasUnevenLength(args)) {
      throw new RuntimeException("The number of given arguments should be even!");
    }
    Map<String, String> arguments = new HashMap<>();
    fillArgumentMapWithDefaultValues(arguments);
    for (int i = 0; i < args.length; i += 2) {
      String identifier = stripLeadingHyphens(args[i]);
      String value = args[i + 1];
      ensureIdentifierIsKnown(arguments, identifier);
      arguments.put(identifier, value);
    }
    return arguments;
  }

  private static void ensureIdentifierIsKnown(Map<String, String> arguments, String identifier) {
    if (!arguments.containsKey(identifier)) {
      throw new RuntimeException("Unknown argument [" + identifier + "]!");
    }
  }

  private static void fillArgumentMapWithDefaultValues(Map<String, String> arguments) {
    arguments.put("numberOfProcessInstances", String.valueOf(100_000));
    arguments.put("engineRest", "http://localhost:8080/engine-rest");
    arguments.put("removeDeployments", "true");
    arguments.put("definitions", getDefaultDefinitions());
  }

  public static String getDefaultDefinitions() {
    try (ScanResult scanResult = new ClassGraph()
      .enableClassInfo()
      .whitelistPackages(DataGenerator.class.getPackage().getName())
      .scan()) {
      ClassInfoList subclasses = scanResult.getSubclasses(DataGenerator.class.getName());
      return subclasses.stream()
        .map(c -> c.getSimpleName().replace("DataGenerator", ""))
        .reduce("", (a, b) -> a + b + ":,", (a, b) -> a + b);
    }
  }

  private static String stripLeadingHyphens(String str) {
    int index = str.lastIndexOf("-");
    if (index != -1) {
      return str.substring(index + 1);
    } else {
      return str;
    }
  }

  private static boolean arrayHasUnevenLength(String[] args) {
    return args.length % 2 != 0;
  }

  private void generateData() {
    DataGenerationExecutor dataGenerationExecutor =
      new DataGenerationExecutor(numberOfProcessInstancesToGenerate, engineRestEndpoint, removeDeployments, definitions);
    dataGenerationExecutor.executeDataGeneration();
    dataGenerationExecutor.awaitDataGenerationTermination();
    logger.info("Finished data generation!");
  }
}
