/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.zbctl.util;

import io.avaje.jsonb.Json;
import io.avaje.jsonb.Jsonb;
import io.camunda.client.impl.CamundaClientCredentials;
import io.camunda.client.impl.oauth.OAuthCredentialsCache;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import org.reflections.Reflections;
import org.reflections.scanners.SubTypesScanner;

public class ReflectConfigGenerator {

  public static final String CONFIG_FILE =
      "clients/zbctl/src/main/resources/META-INF/native-image/io.camunda/zeebe-cli/reflect-config.json";
  public static final String CONSTRUCTOR_NAME = "<init>";
  private static final String PACKAGE_NAME = "io.camunda.client.protocol.rest";
  private static final Jsonb JSONB = Jsonb.builder().build();

  public static void main(final String... args) throws Exception {

    final var reflections = new Reflections(PACKAGE_NAME, new SubTypesScanner(false));
    final var classes = new HashSet<>(reflections.getSubTypesOf(Object.class));
    classes.add(CamundaClientCredentials.class);
    classes.addAll(Arrays.stream(OAuthCredentialsCache.class.getDeclaredClasses()).toList());

    final var reflectionClasses =
        classes.stream().map(ReflectConfigGenerator::generateClass).toList();

    final var jsonString = JSONB.toJson(reflectionClasses);
    final Path filePath = Paths.get(CONFIG_FILE);
    Files.writeString(filePath, jsonString);
    System.out.println("Generated reflect-config.json at: " + filePath);
  }

  private static ClassReflectionConfig generateClass(final Class<?> clazz) {
    final var constructors =
        Arrays.stream(clazz.getConstructors())
            .map(ReflectConfigGenerator::generateConstructors)
            .toList();
    final var methods =
        Arrays.stream(clazz.getMethods()).map(ReflectConfigGenerator::generateMethods).toList();
    final var methodReflections = new ArrayList<MethodReflectionConfig>();
    methodReflections.addAll(constructors);
    methodReflections.addAll(methods);
    return new ClassReflectionConfig(clazz.getName(), true, true, true, methodReflections);
  }

  private static MethodReflectionConfig generateConstructors(final Constructor<?> constructor) {
    return new MethodReflectionConfig(
        CONSTRUCTOR_NAME,
        Arrays.stream(constructor.getParameterTypes()).map(Class::getName).toList());
  }

  private static MethodReflectionConfig generateMethods(final Method method) {
    return new MethodReflectionConfig(
        method.getName(), Arrays.stream(method.getParameterTypes()).map(Class::getName).toList());
  }

  @Json
  public record ClassReflectionConfig(
      String name,
      boolean allDeclaredFields,
      boolean queryAllDeclaredMethods,
      boolean queryAllDeclaredConstructors,
      List<MethodReflectionConfig> methods) {}

  @Json
  public record MethodReflectionConfig(String name, List<String> parameterTypes) {}
}
