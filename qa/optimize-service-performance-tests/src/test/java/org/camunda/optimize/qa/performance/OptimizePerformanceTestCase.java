/* Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.camunda.optimize.qa.performance;

import org.camunda.bpm.engine.impl.util.IoUtil;
import org.camunda.optimize.qa.performance.framework.PerfTestBuilder;
import org.camunda.optimize.qa.performance.framework.PerfTestConfiguration;
import org.camunda.optimize.qa.performance.util.PerfTestException;
import org.camunda.optimize.test.rule.ElasticSearchIntegrationTestRule;
import org.camunda.optimize.test.rule.EmbeddedOptimizeRule;
import org.elasticsearch.action.admin.indices.exists.indices.IndicesExistsResponse;
import org.elasticsearch.client.transport.TransportClient;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Rule;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import static org.elasticsearch.common.xcontent.XContentFactory.jsonBuilder;

public abstract class OptimizePerformanceTestCase {

  private static ElasticSearchIntegrationTestRule elasticSearchRule = new ElasticSearchIntegrationTestRule();

  @ClassRule
  public static EmbeddedOptimizeRule embeddedOptimizeRule = new EmbeddedOptimizeRule();

  private static final String PROPERTIES_FILE_NAME = "perf-test-config.properties";
  protected static PerfTestConfiguration configuration;
  private static TransportClient client = null;

  @BeforeClass
  public static void init() throws IOException {
    Properties properties = loadConfigurationProperties();
    configuration = new PerfTestConfiguration(properties);
    authenticate(configuration);
    elasticSearchRule.init();
    client = elasticSearchRule.getClient();
    configuration.setClient(client);
    prepareOptimizeIndex();
  }

  private static void authenticate(PerfTestConfiguration configuration) {
    String authorizationToken = embeddedOptimizeRule.authenticateAdmin();
    configuration.setAuthorizationToken(authorizationToken);
  }

  @AfterClass
  public static void tearDown() {
    elasticSearchRule.deleteOptimizeIndex();
    client.close();
  }

  private static void prepareOptimizeIndex() throws IOException {
    IndicesExistsResponse response = client.admin().indices().prepareExists(configuration.getOptimizeIndex()).get();
    if (response.isExists()) {
      client.admin().indices().prepareDelete(configuration.getOptimizeIndex()).get();
    }
    client.admin().indices().prepareCreate(configuration.getOptimizeIndex())
      // dynamic mapping should map strings to keyword and not to text
      .addMapping(configuration.getEventType(), jsonBuilder()
        .startObject()
          .startObject(configuration.getEventType())
            .startArray("dynamic_templates")
              .startObject()
                .startObject("strings")
                  .field("match_mapping_type", "string")
                  .startObject("mapping")
                    .field("type", "keyword")
                    .field("ignore_above", 256)
                  .endObject()
                .endObject()
              .endObject()
            .endArray()
          .endObject()
        .endObject()
      )
      .get();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();
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
