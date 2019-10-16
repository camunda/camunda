/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.test.it.extension;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.apache.http.entity.mime.MIME;
import org.camunda.optimize.dto.optimize.query.security.CredentialsDto;
import org.camunda.optimize.exception.OptimizeIntegrationTestException;
import org.camunda.optimize.jetty.EmbeddedCamundaOptimize;
import org.camunda.optimize.rest.providers.OptimizeObjectMapperContextResolver;
import org.camunda.optimize.service.cleanup.OptimizeCleanupScheduler;
import org.camunda.optimize.service.es.ElasticsearchImportJobExecutor;
import org.camunda.optimize.service.es.OptimizeElasticsearchClient;
import org.camunda.optimize.service.util.configuration.ConfigurationReloadable;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.glassfish.jersey.client.ClientProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanUtils;
import org.springframework.context.ApplicationContext;

import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.ClientRequestFilter;
import javax.ws.rs.client.ClientResponseFilter;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.Response;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Optional;

/**
 * This class is wrapper around the embedded optimize to ensure
 * only one instance is used for all tests. Also makes sure the
 * configuration is reloadConfiguration after each test.
 */
public class TestEmbeddedCamundaOptimize extends EmbeddedCamundaOptimize {

  private static final Logger logger = LoggerFactory.getLogger(TestEmbeddedCamundaOptimize.class);
  private static final ObjectMapper configObjectMapper = new ObjectMapper().registerModules(
    new JavaTimeModule(), new Jdk8Module()
  );

  public static final String DEFAULT_USERNAME = "demo";
  public static final String DEFAULT_PASSWORD = "demo";
  private static final String DEFAULT_CONTEXT_LOCATION = "classpath:embeddedOptimizeContext.xml";
  private static final int MAX_LOGGED_BODY_SIZE = 10_000;

  private static String authenticationToken;
  private static TestEmbeddedCamundaOptimize testOptimizeInstance;
  /**
   * This configuration is stored the first time optimize is started
   * and restored before each test, so you can adapt the test
   * to your custom configuration.
   */
  private static String serializedDefaultConfiguration;

  /**
   * Uses the singleton pattern to ensure there is only one
   * optimize instance for all tests.
   */
  public static TestEmbeddedCamundaOptimize getInstance() {
    if (testOptimizeInstance == null) {
      testOptimizeInstance = new TestEmbeddedCamundaOptimize(DEFAULT_CONTEXT_LOCATION);
    }
    return testOptimizeInstance;
  }

  /**
   * If instance is not initialized, initialize it from specific context. Otherwise
   * return existing instance.
   *
   * @param contextLocation - must be not null
   * @return static instance of embedded Optimize
   */
  public static TestEmbeddedCamundaOptimize getInstance(String contextLocation) {
    if (testOptimizeInstance == null) {
      testOptimizeInstance = new TestEmbeddedCamundaOptimize(contextLocation);
    }
    return testOptimizeInstance;
  }

  private TestEmbeddedCamundaOptimize(String contextLocation) {
    super(contextLocation);
  }

  public void start() throws Exception {
    if (!testOptimizeInstance.isOptimizeStarted()) {
      testOptimizeInstance.startOptimize();
      if (isThisTheFirstTimeOptimizeWasStarted()) {
        // store the default configuration to restore it later
        serializedDefaultConfiguration =
          configObjectMapper.writeValueAsString(testOptimizeInstance.getConfigurationService());
      }
      resetConfiguration();
      reloadConfiguration();
    }
    initAuthenticationToken();
  }

  public boolean isStarted() {
    return testOptimizeInstance.isOptimizeStarted();
  }

  private boolean isThisTheFirstTimeOptimizeWasStarted() {
    return serializedDefaultConfiguration == null;
  }

  public void destroy() throws Exception {
    testOptimizeInstance.destroyOptimize();
    testOptimizeInstance = null;
  }

  @Override
  protected ConfigurationService constructConfigurationService() {
    return IntegrationTestConfigurationUtil.createItConfigurationService();
  }

  public void resetConfiguration() throws IOException {
    logger.info("resetting config, parsing defaultconfig and copying properties");
    // copy all properties from the default configuration to the embedded optimize
    BeanUtils.copyProperties(
      configObjectMapper
        .readValue(serializedDefaultConfiguration, ConfigurationService.class),
      testOptimizeInstance.getConfigurationService()
    );
    logger.info("done resetting config");
  }

  public void reloadConfiguration() {
    Map<String, ?> refreshableServices = getApplicationContext().getBeansOfType(ConfigurationReloadable.class);
    for (Map.Entry<String, ?> entry : refreshableServices.entrySet()) {
      Object beanRef = entry.getValue();
      if (beanRef instanceof ConfigurationReloadable) {
        ConfigurationReloadable reloadable = (ConfigurationReloadable) beanRef;
        reloadable.reloadConfiguration(getApplicationContext());
      }
    }
  }

  protected ApplicationContext getApplicationContext() {
    return testOptimizeInstance.getOptimizeApplicationContext();
  }

  public ElasticsearchImportJobExecutor getElasticsearchImportJobExecutor() {
    return getApplicationContext().getBean(ElasticsearchImportJobExecutor.class);
  }

  public ConfigurationService getConfigurationService() {
    return getApplicationContext().getBean(ConfigurationService.class);
  }

  public OptimizeCleanupScheduler getCleanupService() {
    return getApplicationContext().getBean(OptimizeCleanupScheduler.class);
  }

  public OptimizeElasticsearchClient getOptimizeElasticClient() {
    return getApplicationContext().getBean(OptimizeElasticsearchClient.class);
  }

  public DateTimeFormatter getDateTimeFormatter() {
    return getApplicationContext().getBean(DateTimeFormatter.class);
  }

  /**
   * The actual storing is only performed once, when this class is the first time initialized.
   */
  private void initAuthenticationToken() {
    if (authenticationToken == null) {
      authenticationToken = getNewAuthenticationToken()
        .orElseThrow(() -> new OptimizeIntegrationTestException("Could not obtain authentication token."));
    }
  }

  public String getAuthenticationToken() {
    return authenticationToken;
  }

  public Optional<String> getNewAuthenticationToken() {
    return this.authenticateDemoUser();
  }

  private Optional<String> authenticateDemoUser() {
    Response tokenResponse = authenticateDemo();
    if (tokenResponse.getStatus() == HttpServletResponse.SC_OK) {
      return Optional.of(tokenResponse.readEntity(String.class));
    }
    return Optional.empty();
  }

  private Response authenticateDemo() {
    CredentialsDto entity = new CredentialsDto();
    entity.setUsername(DEFAULT_USERNAME);
    entity.setPassword(DEFAULT_PASSWORD);

    return target()
      .path("authentication")
      .request()
      .post(Entity.json(entity));
  }

  public WebTarget target() {
    return getClient().target(IntegrationTestConfigurationUtil.getEmbeddedOptimizeRestApiEndpoint());
  }

  public WebTarget rootTarget() {
    return getClient().target(IntegrationTestConfigurationUtil.getEmbeddedOptimizeEndpoint());
  }

  public final WebTarget rootTarget(String path) {
    return this.rootTarget().path(path);
  }

  public final WebTarget target(String path) {
    return this.target().path(path);
  }

  private Client getClient() {
    // register the default object provider for serialization/deserialization ob objects
    OptimizeObjectMapperContextResolver provider = getApplicationContext()
      .getBean(OptimizeObjectMapperContextResolver.class);

    Client client = ClientBuilder.newClient()
      .register(provider);
    client.register((ClientRequestFilter) requestContext -> logger.info(
      "EmbeddedTestClient request {} {}",
      requestContext.getMethod(),
      requestContext.getUri()
    ));
    client.register((ClientResponseFilter) (requestContext, responseContext) -> {
      if (responseContext.hasEntity()) {
        responseContext.setEntityStream(wrapEntityStreamIfNecessary(responseContext.getEntityStream()));
      }
      logger.debug(
        "EmbeddedTestClient response for {} {}: {}",
        requestContext.getMethod(),
        requestContext.getUri(),
        responseContext.hasEntity() ? serializeBodyCappedToMaxSize(responseContext.getEntityStream()) : ""
      );
    });
    client.property(ClientProperties.CONNECT_TIMEOUT, IntegrationTestConfigurationUtil.getHttpTimeoutMillis());
    client.property(ClientProperties.READ_TIMEOUT, IntegrationTestConfigurationUtil.getHttpTimeoutMillis());
    client.property(ClientProperties.FOLLOW_REDIRECTS, Boolean.FALSE);
    return client;
  }

  private InputStream wrapEntityStreamIfNecessary(final InputStream originalEntityStream) {
    return !originalEntityStream.markSupported() ? new BufferedInputStream(originalEntityStream) : originalEntityStream;
  }

  private String serializeBodyCappedToMaxSize(final InputStream entityStream) throws IOException {
    entityStream.mark(MAX_LOGGED_BODY_SIZE + 1);

    final byte[] entity = new byte[MAX_LOGGED_BODY_SIZE + 1];
    final int entitySize = entityStream.read(entity);
    final StringBuilder stringBuilder = new StringBuilder(
      new String(entity, 0, Math.min(entitySize, MAX_LOGGED_BODY_SIZE), MIME.DEFAULT_CHARSET)
    );
    if (entitySize > MAX_LOGGED_BODY_SIZE) {
      stringBuilder.append("...");
    }
    stringBuilder.append('\n');

    entityStream.reset();
    return stringBuilder.toString();
  }
}