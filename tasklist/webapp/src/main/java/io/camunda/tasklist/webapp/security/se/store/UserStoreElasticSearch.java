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
package io.camunda.tasklist.webapp.security.se.store;

import static io.camunda.tasklist.webapp.security.TasklistProfileService.IDENTITY_AUTH_PROFILE;
import static io.camunda.tasklist.webapp.security.TasklistProfileService.SSO_AUTH_PROFILE;
import static org.elasticsearch.index.query.QueryBuilders.constantScoreQuery;
import static org.elasticsearch.index.query.QueryBuilders.idsQuery;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.tasklist.data.conditionals.ElasticSearchCondition;
import io.camunda.tasklist.entities.UserEntity;
import io.camunda.tasklist.exceptions.TasklistRuntimeException;
import io.camunda.tasklist.schema.indices.UserIndex;
import io.camunda.tasklist.util.ElasticsearchUtil;
import io.camunda.tasklist.webapp.rest.exception.NotFoundApiException;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.index.query.ConstantScoreQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.script.Script;
import org.elasticsearch.script.ScriptType;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.sort.ScriptSortBuilder;
import org.elasticsearch.search.sort.SortBuilders;
import org.elasticsearch.search.sort.SortOrder;
import org.elasticsearch.xcontent.XContentType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("!" + SSO_AUTH_PROFILE + " & !" + IDENTITY_AUTH_PROFILE)
@DependsOn("schemaStartup")
@Conditional(ElasticSearchCondition.class)
public class UserStoreElasticSearch implements UserStore {
  private static final Logger LOGGER = LoggerFactory.getLogger(UserStoreElasticSearch.class);

  private static final XContentType XCONTENT_TYPE = XContentType.JSON;

  @Autowired private UserIndex userIndex;
  @Autowired private RestHighLevelClient esClient;
  @Autowired private ObjectMapper objectMapper;

  @Override
  public UserEntity getByUserId(String userId) {
    final SearchRequest searchRequest =
        new SearchRequest(userIndex.getAlias())
            .source(
                new SearchSourceBuilder()
                    .query(QueryBuilders.termQuery(UserIndex.USER_ID, userId)));
    try {
      final SearchResponse response = esClient.search(searchRequest, RequestOptions.DEFAULT);
      if (response.getHits().getTotalHits().value == 1) {
        return ElasticsearchUtil.fromSearchHit(
            response.getHits().getHits()[0].getSourceAsString(), objectMapper, UserEntity.class);
      } else if (response.getHits().getTotalHits().value > 1) {
        throw new NotFoundApiException(
            String.format("Could not find unique user with userId '%s'.", userId));
      } else {
        throw new NotFoundApiException(
            String.format("Could not find user with userId '%s'.", userId));
      }
    } catch (IOException e) {
      final String message =
          String.format("Exception occurred, while obtaining the user: %s", e.getMessage());
      throw new TasklistRuntimeException(message, e);
    }
  }

  @Override
  public void create(UserEntity user) {
    try {
      final IndexRequest request =
          new IndexRequest(userIndex.getFullQualifiedName())
              .id(user.getId())
              .source(userEntityToJSONString(user), XCONTENT_TYPE);
      esClient.index(request, RequestOptions.DEFAULT);
    } catch (Exception e) {
      LOGGER.error("Could not create user with user id {}", user.getUserId(), e);
    }
  }

  @Override
  public List<UserEntity> getUsersByUserIds(List<String> userIds) {
    final ConstantScoreQueryBuilder esQuery =
        constantScoreQuery(idsQuery().addIds(userIds.toArray(String[]::new)));

    final SearchRequest searchRequest =
        new SearchRequest(userIndex.getAlias())
            .source(
                new SearchSourceBuilder()
                    .query(esQuery)
                    .sort(
                        SortBuilders.scriptSort(
                                getScript(userIds), ScriptSortBuilder.ScriptSortType.NUMBER)
                            .order(SortOrder.ASC))
                    .fetchSource(new String[] {UserIndex.USER_ID, UserIndex.DISPLAY_NAME}, null));

    try {
      return ElasticsearchUtil.scroll(searchRequest, UserEntity.class, objectMapper, esClient);
    } catch (IOException e) {
      final String message =
          String.format("Exception occurred, while obtaining users: %s", e.getMessage());
      throw new TasklistRuntimeException(message, e);
    }
  }

  private Script getScript(final List<String> userIds) {
    final String scriptCode =
        String.format(
            "def userIdsCount = params.userIds.size();"
                + "def userId = doc['%s'].value;"
                + "def foundIdx = params.userIds.indexOf(userId);"
                + "return foundIdx > -1 ? foundIdx: userIdsCount + 1;",
            UserIndex.USER_ID);
    return new Script(
        ScriptType.INLINE, Script.DEFAULT_SCRIPT_LANG, scriptCode, Map.of("userIds", userIds));
  }

  protected String userEntityToJSONString(UserEntity aUser) throws JsonProcessingException {
    return objectMapper.writeValueAsString(aUser);
  }
}
