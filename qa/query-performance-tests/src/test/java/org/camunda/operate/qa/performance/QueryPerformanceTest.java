/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.qa.performance;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.apache.http.client.CookieStore;
import org.apache.http.client.protocol.HttpClientContext;
import org.camunda.operate.qa.performance.util.StatefulRestTemplate;
import org.camunda.operate.qa.performance.util.URLUtil;
import org.camunda.operate.util.CollectionUtil;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestContextManager;
import org.springframework.web.client.HttpClientErrorException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import static junit.framework.TestCase.fail;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assume.assumeTrue;

/**
 * The tests reads QUERIES_PATH folder and parse all files contained there, reading queries to be tested ({@see org.camunda.operate.qa.performance.TestQuery}).
 */
@RunWith(Parameterized.class)
@ContextConfiguration(classes = { TestConfig.class })
public class QueryPerformanceTest {

  //  private static final Logger logger = LoggerFactory.getLogger(ImportPerformanceStaticDataTest.class);

  private static final String QUERIES_PATH = "/queries";

  // Manually config for spring to use Parameterised
  private TestContextManager testContextManager;

  @Autowired
  private StatefulRestTemplate restTemplate;

  @Value("${camunda.operate.qa.queries.operate.username}")
  private String username;

  @Value("${camunda.operate.qa.queries.operate.password}")
  private String password;

  @Value("${camunda.operate.qa.queries.timeout:3000L}")
  private long timeout;

  @Autowired
  private URLUtil urlUtil;

  @Autowired
  private ParametersResolver parametersResolver;

  @Parameterized.Parameter
  public TestQuery testQuery;

  @Before
  public void init() {
    this.testContextManager = new TestContextManager(getClass());
    try {
      this.testContextManager.prepareTestInstance(this);
    } catch (Exception e) {
      throw new RuntimeException("Failed to initialize context manager", e);
    }
    //log in only once
    final CookieStore cookieStore = (CookieStore)restTemplate.getHttpContext().getAttribute(HttpClientContext.COOKIE_STORE);
    if (cookieStore.getCookies().isEmpty()) {
      login();
    }
  }

  private void login() {
    HttpEntity<Map<String,Object>> requestEntity = new HttpEntity<>(CollectionUtil.asMap("username",username,
                                                                                         "password",password));
    ResponseEntity<Object> response = restTemplate.postForEntity(urlUtil.getURL("/api/login?username=demo&password=demo"), requestEntity, Object.class);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
  }

  @Parameterized.Parameters(name = "{0}")
  public static Collection<TestQuery> readQueries() {
    final ObjectMapper objectMapper = new ObjectMapper();
    objectMapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    Collection<TestQuery> result = new ArrayList<>();
    try (Stream<Path> paths = Files.walk(Paths.get(QueryPerformanceTest.class.getResource(QUERIES_PATH).toURI()))) {
      paths.filter(Files::isRegularFile).forEach(path -> {
        try (InputStream is = Files.newInputStream(path)) {
          result.addAll(objectMapper.readValue(is, new TypeReference<List<TestQuery>>(){}));
        } catch (Exception ex) {
          throw new RuntimeException("Error occurred when reading queries from files", ex);
        }
      });
    } catch (Exception ex) {
      throw new RuntimeException("Error occurred when reading queries from files", ex);
    }
    return result;
  }

  @Test
  public void testQuery () {
    assumeTrue(testQuery.getIgnore(), testQuery.getIgnore() == null);

//    logger.info("Running query {}", testQuery.getTitle());
    parametersResolver.replacePlaceholdersInQuery(testQuery);

    Instant start = Instant.now();
    try {
      RequestEntity<String> requestEntity =
          RequestEntity.method(testQuery.getMethod(), urlUtil.getURL(testQuery.getUrl(), testQuery.getPathParams()))
              .contentType(MediaType.APPLICATION_JSON)
              .body(testQuery.getBody());
      start = Instant.now();
      ResponseEntity<String> response = restTemplate.exchange(requestEntity, String.class);
      assertThat(response.getStatusCode()).as(testQuery.getTitle() + " (status)").isEqualTo(HttpStatus.OK);
    } catch (HttpClientErrorException ex) {
      fail(String.format("Query %s failed with the error: %s", testQuery.getTitle(), ex.getResponseBodyAsString()));
    }
    Instant finish = Instant.now();
    long timeElapsed = Duration.between(start, finish).toMillis();
    //    logger.info("Running of query took {} milliseconds", timeElapsed);

    assertThat(timeElapsed).as(testQuery.getTitle() + " (duration)").isLessThan(timeout);
  }

}
