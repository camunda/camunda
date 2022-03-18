/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.data.generation;

import io.github.classgraph.ClassGraph;
import io.github.classgraph.ClassInfoList;
import io.github.classgraph.ScanResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.camunda.optimize.data.generation.generators.DBConnector;
import org.camunda.optimize.data.generation.generators.DataGenerator;
import org.camunda.optimize.data.generation.generators.dto.DataGenerationInformation;
import org.camunda.optimize.data.generation.generators.impl.decision.DecisionDataGenerator;
import org.camunda.optimize.data.generation.generators.impl.process.ProcessDataGenerator;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@RequiredArgsConstructor
public class DataGenerationMain {

  private final DataGenerationInformation dataGenerationInformation;

  private static final String DB_URL_H2_TEMPLATE = "jdbc:h2:tcp://localhost:9092/./camunda-h2-dbs/process-engine";
  private static final String JDBC_DRIVER = "org.h2.Driver";
  private static final String USER_H2 = "sa";
  private static final String PASS_H2 = "sa";


  public static void main(String[] args) throws ParseException {
    final Map<String, String> arguments = extractArguments(args);
    DataGenerationInformation dataGenerationInformation = extractDataGenerationInformation(arguments);
    DataGenerationMain main = new DataGenerationMain(dataGenerationInformation);
    validateProcessInstanceDateParameters(arguments);
    main.generateData();
    updateProcessInstanceDatesIfRequired(arguments);
  }

  public void generateData() {
    DataGenerationExecutor dataGenerationExecutor =
      new DataGenerationExecutor(dataGenerationInformation);
    log.info("Start generating data...");
    dataGenerationExecutor.executeDataGeneration();
    dataGenerationExecutor.awaitDataGenerationTermination();
    log.info("Finished data generation!");
  }

  private static void validateProcessInstanceDateParameters(final Map<String, String> arguments) throws ParseException {
    if (Boolean.parseBoolean(arguments.get("adjustProcessInstanceDates"))) {
      checkDateSpectrum(arguments.get("startDate"), arguments.get("endDate"));
    }
  }

  private static DataGenerationInformation extractDataGenerationInformation(Map<String, String> arguments) {
    // argument is being adjusted
    long processInstanceCountToGenerate =
      Long.parseLong(arguments.get("numberOfProcessInstances"));
    long decisionInstanceCountToGenerate =
      Long.parseLong(arguments.get("numberOfDecisionInstances"));
    String engineRestEndpoint = arguments.get("engineRest");
    boolean removeDeployments = Boolean.parseBoolean(arguments.get("removeDeployments"));
    Map<String, Integer> processDefinitions = parseDefinitions(arguments.get("processDefinitions"));
    Map<String, Integer> decisionDefinitions = parseDefinitions(arguments.get("decisionDefinitions"));

    return DataGenerationInformation.builder()
      .processInstanceCountToGenerate(processInstanceCountToGenerate)
      .decisionInstanceCountToGenerate(decisionInstanceCountToGenerate)
      .engineRestEndpoint(engineRestEndpoint)
      .processDefinitionsAndNumberOfVersions(processDefinitions)
      .decisionDefinitionsAndNumberOfVersions(decisionDefinitions)
      .removeDeployments(removeDeployments)
      .build();
  }

  public static Map<String, Integer> parseDefinitions(String definitions) {
    final Map<String, Integer> res = new HashMap<>();
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

  private static void checkDateSpectrum(String startDate, String endDate) throws ParseException {
    SimpleDateFormat format = new SimpleDateFormat("dd/MM/yyyy");
    try {
      Date startDateObject = format.parse(startDate);
      Date endDateObject = format.parse(endDate);

      if (startDateObject.compareTo(endDateObject) >= 0) {
        throw new IllegalArgumentException("startDate argument cannot be greater than endDate");
      }
    } catch (ParseException e) {
      throw new ParseException("There was an error while parsing the dates", e.getErrorOffset());
    }
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
    arguments.put("adjustProcessInstanceDates", "false");
    arguments.put("startDate", "01/01/2018");
    arguments.put("endDate", "01/01/2020");
    arguments.put("jdbcDriver", JDBC_DRIVER);
    arguments.put("dbUrl", DB_URL_H2_TEMPLATE);
    arguments.put("dbUser", USER_H2);
    arguments.put("dbPassword", PASS_H2);
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

  private static void updateProcessInstanceDatesIfRequired(final Map<String, String> arguments) {
    if (Boolean.parseBoolean(arguments.get("adjustProcessInstanceDates"))) {
      DBConnector dbConnector = new DBConnector(
        arguments.get("jdbcDriver"),
        arguments.get("dbUrl"),
        arguments.get("dbUser"),
        arguments.get("dbPassword")
      );
      dbConnector.updateProcessInstances(arguments.get("startDate"), arguments.get("endDate"));
      log.info("Updated endDate and startDate of process instances in db!");
    } else {
      log.info("Skipping process instance start- and endDate adjustments.");
    }
  }
}
