/*
 * Copyright Camunda Services GmbH
 *
 * BY INSTALLING, DOWNLOADING, ACCESSING, USING, OR DISTRIBUTING THE SOFTWARE (“USE”), YOU INDICATE YOUR ACCEPTANCE TO AND ARE ENTERING INTO A CONTRACT WITH, THE LICENSOR ON THE TERMS SET OUT IN THIS AGREEMENT. IF YOU DO NOT AGREE TO THESE TERMS, YOU MUST NOT USE THE SOFTWARE. IF YOU ARE RECEIVING THE SOFTWARE ON BEHALF OF A LEGAL ENTITY, YOU REPRESENT AND WARRANT THAT YOU HAVE THE ACTUAL AUTHORITY TO AGREE TO THE TERMS AND CONDITIONS OF THIS AGREEMENT ON BEHALF OF SUCH ENTITY.
 * “Licensee” means you, an individual, or the entity on whose behalf you receive the Software.
 *
 * Permission is hereby granted, free of charge, to the Licensee obtaining a copy of this Software and associated documentation files to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject in each case to the following conditions:
 * Condition 1: If the Licensee distributes the Software or any derivative works of the Software, the Licensee must attach this Agreement.
 * Condition 2: Without limiting other conditions in this Agreement, the grant of rights is solely for non-production use as defined below.
 * "Non-production use" means any use of the Software that is not directly related to creating products, services, or systems that generate revenue or other direct or indirect economic benefits.  Examples of permitted non-production use include personal use, educational use, research, and development. Examples of prohibited production use include, without limitation, use for commercial, for-profit, or publicly accessible systems or use for commercial or revenue-generating purposes.
 *
 * If the Licensee is in breach of the Conditions, this Agreement, including the rights granted under it, will automatically terminate with immediate effect.
 *
 * SUBJECT AS SET OUT BELOW, THE SOFTWARE IS PROVIDED “AS IS”, WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE, AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 * NOTHING IN THIS AGREEMENT EXCLUDES OR RESTRICTS A PARTY’S LIABILITY FOR (A) DEATH OR PERSONAL INJURY CAUSED BY THAT PARTY’S NEGLIGENCE, (B) FRAUD, OR (C) ANY OTHER LIABILITY TO THE EXTENT THAT IT CANNOT BE LAWFULLY EXCLUDED OR RESTRICTED.
 */
package io.camunda.operate.qa.performance;

import static junit.framework.TestCase.fail;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assume.assumeTrue;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.operate.util.rest.StatefulRestTemplate;
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

/**
 * The tests reads QUERIES_PATH folder and parse all files contained there, reading queries to be
 * tested ({@see io.camunda.operate.qa.performance.TestQuery}).
 */
@RunWith(Parameterized.class)
@ContextConfiguration(classes = {TestConfig.class})
public class QueryPerformanceTest {

  //  private static final Logger logger =
  // LoggerFactory.getLogger(ImportPerformanceStaticDataTest.class);

  private static final String QUERIES_PATH = "/queries";
  @Parameterized.Parameter public TestQuery testQuery;
  // Manually config for spring to use Parameterised
  private TestContextManager testContextManager;
  @Autowired private BiFunction<String, Integer, StatefulRestTemplate> statefulRestTemplateFactory;
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

  @Autowired private ParametersResolver parametersResolver;

  @Parameterized.Parameters(name = "{0}")
  public static Collection<TestQuery> readQueries() {
    final ObjectMapper objectMapper = new ObjectMapper();
    objectMapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    Collection<TestQuery> result = new ArrayList<>();
    try (Stream<Path> paths =
        Files.walk(Paths.get(QueryPerformanceTest.class.getResource(QUERIES_PATH).toURI()))) {
      paths
          .filter(Files::isRegularFile)
          .forEach(
              path -> {
                try (InputStream is = Files.newInputStream(path)) {
                  result.addAll(
                      objectMapper.readValue(is, new TypeReference<List<TestQuery>>() {}));
                } catch (Exception ex) {
                  throw new RuntimeException("Error occurred when reading queries from files", ex);
                }
              });
    } catch (Exception ex) {
      throw new RuntimeException("Error occurred when reading queries from files", ex);
    }
    return result;
  }

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

  @Test
  public void testQuery() {
    assumeTrue(testQuery.getIgnore(), testQuery.getIgnore() == null);

    //    logger.info("Running query {}", testQuery.getTitle());
    parametersResolver.replacePlaceholdersInQuery(testQuery);

    Instant start = Instant.now();
    try {
      RequestEntity<String> requestEntity =
          RequestEntity.method(
                  testQuery.getMethod(),
                  restTemplate.getURL(testQuery.getUrl(), testQuery.getPathParams()))
              .contentType(MediaType.APPLICATION_JSON)
              .body(testQuery.getBody());
      start = Instant.now();
      ResponseEntity<String> response = restTemplate.exchange(requestEntity, String.class);
      assertThat(response.getStatusCode())
          .as(testQuery.getTitle() + " (status)")
          .isEqualTo(HttpStatus.OK);
    } catch (HttpClientErrorException ex) {
      fail(
          String.format(
              "Query %s failed with the error: %s",
              testQuery.getTitle(), ex.getResponseBodyAsString()));
    }
    Instant finish = Instant.now();
    long timeElapsed = Duration.between(start, finish).toMillis();
    //    logger.info("Running of query took {} milliseconds", timeElapsed);

    assertThat(timeElapsed).as(testQuery.getTitle() + " (duration)").isLessThan(timeout);
  }
}
