/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize;

import static io.camunda.optimize.service.util.configuration.EnvironmentPropertiesConstants.CONTEXT_PATH;
import static io.camunda.optimize.service.util.configuration.EnvironmentPropertiesConstants.HTTPS_PORT_KEY;
import static io.camunda.optimize.service.util.configuration.EnvironmentPropertiesConstants.HTTP_PORT_KEY;
import static io.camunda.optimize.service.util.configuration.EnvironmentPropertiesConstants.INTEGRATION_TESTS;
import static io.camunda.optimize.tomcat.OptimizeResourceConstants.ACTUATOR_PORT_PROPERTY_KEY;

import io.camunda.optimize.test.it.extension.DatabaseIntegrationTestExtension;
import io.camunda.optimize.test.it.extension.EmbeddedOptimizeExtension;
import io.camunda.optimize.test.optimize.VariablesClient;
import io.camunda.optimize.tomcat.OptimizeResourceConstants;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT,
    properties = {
      INTEGRATION_TESTS + "=true",
    })
@Configuration
public abstract class AbstractIT {

  // All tests marked with this tag are passing with OpenSearch
  public static final String OPENSEARCH_PASSING = "openSearchPassing";
  // Tests marked with this tag are known to not be working with OpenSearch and are not expected to
  // be working yet
  public static final String OPENSEARCH_SINGLE_TEST_FAIL_OK = "openSearchSingleTestFailOK";
  // Tests marked with this tag are tests that should be working with OpenSearch, but are failing
  // due to a bug. They
  // are ignored by the 'OpenSearch passing' CI pipeline, but need to be addressed soon
  public static final String OPENSEARCH_SHOULD_BE_PASSING = "openSearchShouldBePassing";

  @Autowired private Environment environment;

  @RegisterExtension
  @Order(1)
  public static DatabaseIntegrationTestExtension databaseIntegrationTestExtension =
      new DatabaseIntegrationTestExtension();

  @RegisterExtension
  @Order(3)
  public static EmbeddedOptimizeExtension embeddedOptimizeExtension =
      new EmbeddedOptimizeExtension();

  // optimize test helpers
  protected VariablesClient variablesClient = new VariablesClient();

  protected abstract void startAndUseNewOptimizeInstance();

  protected void startAndUseNewOptimizeInstance(
      final Map<String, String> argMap, final String activeProfile) {
    final String[] arguments = prepareArgs(argMap);

    // run after-test cleanups with the old context
    embeddedOptimizeExtension.afterTest();
    // in case it's not the first *additional* instance, we terminate the first one
    if (embeddedOptimizeExtension.isCloseContextAfterTest()) {
      ((ConfigurableApplicationContext) embeddedOptimizeExtension.getApplicationContext()).close();
    }

    final ConfigurableApplicationContext context =
        new SpringApplicationBuilder(Main.class).profiles(activeProfile).build().run(arguments);

    embeddedOptimizeExtension.setApplicationContext(context);
    embeddedOptimizeExtension.setCloseContextAfterTest(true);
    embeddedOptimizeExtension.setResetImportOnStart(false);
    embeddedOptimizeExtension.setupOptimize();
  }

  private String[] prepareArgs(final Map<String, String> argMap) {
    final String httpsPort = getPortArg(HTTPS_PORT_KEY);
    String httpPort = getPortArg(HTTP_PORT_KEY);

    final String actuatorPort =
        getArg(
            ACTUATOR_PORT_PROPERTY_KEY,
            String.valueOf(OptimizeResourceConstants.ACTUATOR_PORT + 100));
    final String contextPath =
        embeddedOptimizeExtension
            .getConfigurationService()
            .getContextPath()
            .map(contextPathFromConfig -> getArg(CONTEXT_PATH, contextPathFromConfig))
            .orElse("");

    final List<String> argList =
        argMap.entrySet().stream()
            .map(e -> getArg(e.getKey(), e.getValue()))
            .collect(Collectors.toList());

    Collections.addAll(argList, httpsPort, httpPort, actuatorPort, contextPath);

    return argList.toArray(String[]::new);
  }

  private String getPortArg(final String portKey) {
    return getArg(
        portKey,
        String.valueOf(
            embeddedOptimizeExtension.getBean(OptimizeTomcatConfig.class).getPort(portKey) + 100));
  }

  private String getArg(final String key, final String value) {
    return String.format("--%s=%s", key, value);
  }
}
