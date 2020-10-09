/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.data.generation;

import io.github.classgraph.ClassGraph;
import io.github.classgraph.ClassInfoList;
import io.github.classgraph.ScanResult;
import lombok.AllArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.camunda.optimize.data.generation.generators.DataGenerator;
import org.camunda.optimize.data.generation.generators.dto.DataGenerationInformation;
import org.camunda.optimize.data.generation.generators.impl.decision.DecisionDataGenerator;
import org.camunda.optimize.data.generation.generators.impl.process.ProcessDataGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

@AllArgsConstructor
public class DataGenerationMain {

  private final Logger logger = LoggerFactory.getLogger(getClass());

  private final DataGenerationInformation dataGenerationInformation;

  public static void main(String[] args) {
    DataGenerationInformation dataGenerationInformation = extractDataGenerationInformation(args);
    DataGenerationMain main = new DataGenerationMain(dataGenerationInformation);
    main.generateData();
    System.exit(0);
  }

  private static DataGenerationInformation extractDataGenerationInformation(final String[] args) {
    Map<String, String> arguments = extractArguments(args);

    // argument is being adjusted
    long processInstanceCountToGenerate =
      Long.parseLong(arguments.get("numberOfProcessInstances"));
    long decisionInstanceCountToGenerate =
      Long.parseLong(arguments.get("numberOfDecisionInstances"));
    String engineRestEndpoint = arguments.get("engineRest");
    boolean removeDeployments = Boolean.parseBoolean(arguments.get("removeDeployments"));
    HashMap<String, Integer> processDefinitions = parseDefinitions(arguments.get("processDefinitions"));
    HashMap<String, Integer> decisionDefinitions = parseDefinitions(arguments.get("decisionDefinitions"));

    return new DataGenerationInformation()
      .setProcessInstanceCountToGenerate(processInstanceCountToGenerate)
      .setDecisionInstanceCountToGenerate(decisionInstanceCountToGenerate)
      .setEngineRestEndpoint(engineRestEndpoint)
      .setProcessDefinitions(processDefinitions)
      .setDecisionDefinitions(decisionDefinitions)
      .setRemoveDeployments(removeDeployments);
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

  private static Map<String, String> extractArguments(final String[] args) {
    final Map<String, String> arguments = new HashMap<>();
    fillArgumentMapWithDefaultValues(arguments);
    for (int i = 0; i < args.length; i++) {
      final String identifier = stripLeadingHyphens(args[i]);
      ensureIdentifierIsKnown(arguments, identifier);
      final String value = args.length > i + 1 ? args[i + 1] : null;
      if (!StringUtils.isBlank(value) && value.indexOf("--") != 0) {
        arguments.put(identifier, value);
        // increase i one further as we have a value argument here
        i += 1;
      }
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
    arguments.put("numberOfDecisionInstances", String.valueOf(10_000));
    arguments.put("engineRest", "http://localhost:8080/engine-rest");
    arguments.put("removeDeployments", "true");
    arguments.put("processDefinitions", getDefaultDefinitionsOfClass(ProcessDataGenerator.class));
    arguments.put("decisionDefinitions", getDefaultDefinitionsOfClass(DecisionDataGenerator.class));
  }

  public static String getDefaultDefinitionsOfClass(Class<? extends DataGenerator<?>> classToExtractDefinitionsFor) {
    try (ScanResult scanResult = new ClassGraph()
      .enableClassInfo()
      .acceptPackages(DataGenerator.class.getPackage().getName())
      .scan()) {
      ClassInfoList subclasses = scanResult.getSubclasses(classToExtractDefinitionsFor.getName());
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

  private void generateData() {
    DataGenerationExecutor dataGenerationExecutor =
      new DataGenerationExecutor(dataGenerationInformation);
    logger.info("Start generating data...");
    dataGenerationExecutor.executeDataGeneration();
    dataGenerationExecutor.awaitDataGenerationTermination();
    logger.info("Finished data generation!");
  }
}
