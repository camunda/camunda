/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.qa.performance;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.BiFunction;
import java.util.stream.Stream;
import io.camunda.operate.util.rest.StatefulRestTemplate;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
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
 * The tests reads QUERIES_PATH folder and parse all files contained there, reading queries to be tested ({@see io.camunda.operate.qa.performance.TestQuery}).
 */
@RunWith(Parameterized.class)
@ContextConfiguration(classes = { TestConfig.class })
public class QueryPerformanceTest {

  //  private static final Logger logger = LoggerFactory.getLogger(ImportPerformanceStaticDataTest.class);

  private static final String QUERIES_PATH = "/queries";

  // Manually config for spring to use Parameterised
  private TestContextManager testContextManager;

  @Autowired
  private BiFunction<String, Integer, StatefulRestTemplate> statefulRestTemplateFactory;
  private StatefulRestTemplate restTemplate;

  @Value("${camunda.operate.qa.queries.operate.username}")
  private String username;

  @Value("${camunda.operate.qa.queries.operate.password}")
  private String password;

  @Value("${camunda.operate.qa.queries.timeout:3000L}")
  private long timeout;

  @Value("${camunda.operate.qa.queries.operate.host:localhost}")
  private String operateHost;

  @Value("${camunda.operate.qa.queries.operate.port:8080}")
  private Integer operatePort;

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
    restTemplate = statefulRestTemplateFactory.apply(operateHost, operatePort);
    restTemplate.loginWhenNeeded(username, password);
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
          RequestEntity.method(testQuery.getMethod(), restTemplate.getURL(testQuery.getUrl(), testQuery.getPathParams()))
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
