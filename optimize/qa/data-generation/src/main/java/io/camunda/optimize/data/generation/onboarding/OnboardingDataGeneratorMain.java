/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.data.generation.onboarding;

import io.camunda.optimize.service.exceptions.DataGenerationException;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

@Slf4j
public class OnboardingDataGeneratorMain {

  private static final Map<String, OnboardingDataGeneratorParameters> DEFAULT_PARAMS =
      Map.of(
          "numberOfProcessInstancesEndEvent1",
          new OnboardingDataGeneratorParameters(
              String.valueOf(333), "onboarding-data/process-instance-endEvent1.json"),
          "numberOfProcessInstancesEndEvent2",
          new OnboardingDataGeneratorParameters(
              String.valueOf(333), "onboarding-data/process-instance-endEvent2.json"),
          "numberOfProcessInstancesEndEvent3",
          new OnboardingDataGeneratorParameters(
              String.valueOf(333), "onboarding-data/process-instance-endEvent3.json"));

  public static void main(final String[] args) {
    final Map<String, OnboardingDataGeneratorParameters> arguments = extractArguments(args);
    final OnboardingDataGenerator onboardingDataGenerator = new OnboardingDataGenerator();
    onboardingDataGenerator.executeDataGeneration(arguments);
  }

  private static Map<String, OnboardingDataGeneratorParameters> extractArguments(
      final String[] args) {
    final Map<String, OnboardingDataGeneratorParameters> arguments =
        fillArgumentMapWithDefaultValues();
    for (int i = 0; i < args.length; i += 2) {
      final String identifier = stripLeadingHyphens(args[i]);
      ensureIdentifierIsKnown(identifier);
      final String value = args.length > i + 1 ? args[i + 1] : null;
      if (!StringUtils.isBlank(value) && value.indexOf("--") != 0) {
        arguments.get(identifier).setNumberOfProcessInstances(value);
      }
    }
    return arguments;
  }

  private static Map<String, OnboardingDataGeneratorParameters> fillArgumentMapWithDefaultValues() {
    return DEFAULT_PARAMS.entrySet().stream()
        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
  }

  private static String stripLeadingHyphens(final String str) {
    final int index = str.lastIndexOf("-");
    if (index != -1) {
      return str.substring(index + 1);
    } else {
      return str;
    }
  }

  private static void ensureIdentifierIsKnown(final String identifier) {
    if (!DEFAULT_PARAMS.containsKey(identifier)) {
      throw new DataGenerationException("Unknown argument [" + identifier + "]!");
    }
  }
}
