package org.camunda.optimize.qa.performance;

import org.camunda.bpm.engine.impl.util.IoUtil;
import org.camunda.optimize.qa.performance.framework.PerfTestBuilder;
import org.camunda.optimize.qa.performance.framework.PerfTestConfiguration;
import org.camunda.optimize.qa.performance.util.PerfTestException;
import org.camunda.optimize.test.rule.ElasticSearchIntegrationTestRule;
import org.camunda.optimize.test.rule.EmbeddedOptimizeRule;
import org.elasticsearch.action.admin.indices.exists.indices.IndicesExistsResponse;
import org.elasticsearch.client.transport.TransportClient;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.rules.RuleChain;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import static org.elasticsearch.common.xcontent.XContentFactory.jsonBuilder;

public abstract class OptimizePerformanceTestCase {

  public ElasticSearchIntegrationTestRule elasticSearchRule = new ElasticSearchIntegrationTestRule();
  public EmbeddedOptimizeRule embeddedOptimizeRule = new EmbeddedOptimizeRule();

  @Rule
  public RuleChain chain = RuleChain
      .outerRule(elasticSearchRule).around(embeddedOptimizeRule);

  private static final String PROPERTIES_FILE_NAME;

  static {
    PROPERTIES_FILE_NAME = "perf-test-config.properties";
  }

  protected PerfTestConfiguration configuration;

  @Before
  public void init() throws IOException {
    Properties properties = loadConfigurationProperties();
    configuration = new PerfTestConfiguration(properties);
    authenticate(configuration);
    configuration.setClient(elasticSearchRule.getClient());
  }

  private void authenticate(PerfTestConfiguration configuration) {
    String authorizationToken = embeddedOptimizeRule.authenticateAdmin();
    configuration.setAuthorizationToken(authorizationToken);
  }

  private static Properties loadConfigurationProperties() {
    Properties properties = null;
    InputStream propertyInputStream = null;
    try {
      propertyInputStream = OptimizePerformanceTestCase.class.getClassLoader().getResourceAsStream(PROPERTIES_FILE_NAME);
      properties = new Properties();
      properties.load(propertyInputStream);
    } catch (Exception e) {
      throw new PerfTestException("Cannot load properties from file " + PROPERTIES_FILE_NAME + ": " + e);
    } finally {
      IoUtil.closeSilently(propertyInputStream);
    }
    return properties;
  }

  protected PerfTestBuilder createPerformanceTest() {
    return PerfTestBuilder.createPerfTest(configuration);
  }


}
