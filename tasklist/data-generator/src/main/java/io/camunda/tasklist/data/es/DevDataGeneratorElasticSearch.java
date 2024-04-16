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
package io.camunda.tasklist.data.es;

import static java.util.Arrays.asList;

import io.camunda.tasklist.data.DataGenerator;
import io.camunda.tasklist.data.DevDataGeneratorAbstract;
import io.camunda.tasklist.data.conditionals.ElasticSearchCondition;
import io.camunda.tasklist.entities.UserEntity;
import java.io.IOException;
import java.util.List;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.support.IndicesOptions;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.client.indices.GetIndexRequest;
import org.elasticsearch.xcontent.XContentType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("dev-data")
@Conditional(ElasticSearchCondition.class)
public class DevDataGeneratorElasticSearch extends DevDataGeneratorAbstract
    implements DataGenerator {

  private static final Logger LOGGER = LoggerFactory.getLogger(DevDataGeneratorElasticSearch.class);

  @Autowired
  @Qualifier("zeebeEsClient")
  private RestHighLevelClient zeebeEsClient;

  @Autowired private RestHighLevelClient esClient;

  public void createUser(String username, String firstname, String lastname) {
    final String password = username;
    final String passwordEncoded = passwordEncoder.encode(password);
    final UserEntity user =
        UserEntity.from(username, passwordEncoded, List.of("USER"))
            .setDisplayName(firstname + " " + lastname)
            .setRoles(asList("OWNER"));
    try {
      final IndexRequest request =
          new IndexRequest(userIndex.getFullQualifiedName())
              .id(user.getId())
              .source(userEntityToJSONString(user), XContentType.JSON);
      esClient.index(request, RequestOptions.DEFAULT);
    } catch (Exception t) {
      LOGGER.error("Could not create demo user with user id {}", user.getUserId(), t);
    }
    LOGGER.info("Created demo user {} with password {}", username, password);
  }

  public boolean shouldCreateData() {
    try {
      final GetIndexRequest request =
          new GetIndexRequest(tasklistProperties.getZeebeElasticsearch().getPrefix() + "*");
      request.indicesOptions(IndicesOptions.fromOptions(true, false, true, false));
      final boolean exists = zeebeEsClient.indices().exists(request, RequestOptions.DEFAULT);
      if (exists) {
        // data already exists
        LOGGER.debug("Data already exists in Zeebe.");
        return false;
      }
    } catch (IOException io) {
      LOGGER.debug(
          "Error occurred while checking existance of data in Zeebe: {}. Demo data won't be created.",
          io.getMessage());
      return false;
    }
    return true;
  }
}
