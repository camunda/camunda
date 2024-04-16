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

import io.camunda.tasklist.data.conditionals.OpenSearchCondition;
import io.camunda.tasklist.entities.UserEntity;
import io.camunda.tasklist.exceptions.TasklistRuntimeException;
import io.camunda.tasklist.schema.indices.UserIndex;
import io.camunda.tasklist.util.OpenSearchUtil;
import io.camunda.tasklist.webapp.rest.exception.NotFoundApiException;
import java.io.IOException;
import java.util.List;
import org.elasticsearch.index.query.ConstantScoreQueryBuilder;
import org.opensearch.client.json.JsonData;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch._types.ScriptSort;
import org.opensearch.client.opensearch._types.ScriptSortType;
import org.opensearch.client.opensearch._types.SortOrder;
import org.opensearch.client.opensearch.core.IndexRequest;
import org.opensearch.client.opensearch.core.SearchRequest;
import org.opensearch.client.opensearch.core.SearchResponse;
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
@Conditional(OpenSearchCondition.class)
public class UserStoreOpenSearch implements UserStore {
  private static final Logger LOGGER = LoggerFactory.getLogger(UserStoreOpenSearch.class);

  @Autowired private UserIndex userIndex;
  @Autowired private OpenSearchClient openSearchClient;

  @Override
  public UserEntity getByUserId(String userId) {
    final SearchRequest searchRequest =
        new SearchRequest.Builder()
            .index(userIndex.getAlias())
            .query(q -> q.term(t -> t.field(UserIndex.USER_ID).value(v -> v.stringValue(userId))))
            .build();

    try {
      final SearchResponse<UserEntity> response =
          openSearchClient.search(searchRequest, UserEntity.class);
      final var totalHits = response.hits().total().value();

      if (totalHits == 1) {
        return response.hits().hits().stream().findFirst().orElseThrow().source();
      }

      throw new NotFoundApiException(
          totalHits > 1
              ? String.format("Could not find unique user with userId '%s'.", userId)
              : String.format("Could not find user with userId '%s'.", userId));
    } catch (IOException e) {
      final String message =
          String.format("Exception occurred, while obtaining the user: %s", e.getMessage());
      throw new TasklistRuntimeException(message, e);
    }
  }

  @Override
  public void create(UserEntity user) {
    try {
      final IndexRequest<UserEntity> request =
          IndexRequest.of(
              builder ->
                  builder.index(userIndex.getFullQualifiedName()).id(user.getId()).document(user));

      openSearchClient.index(request);
    } catch (Exception e) {
      LOGGER.error("Could not create user with user id {}", user.getUserId(), e);
    }
  }

  @Override
  public List<UserEntity> getUsersByUserIds(List<String> userIds) {
    final ConstantScoreQueryBuilder esQuery =
        constantScoreQuery(idsQuery().addIds(userIds.toArray(String[]::new)));

    final SearchRequest.Builder searchRequest =
        new SearchRequest.Builder()
            .index(userIndex.getAlias())
            .query(q -> q.constantScore(qs -> qs.filter(qf -> qf.ids(iq -> iq.values(userIds)))))
            .sort(s -> s.script(getScriptSort(userIds)))
            .source(s -> s.filter(sf -> sf.includes(UserIndex.USER_ID, UserIndex.DISPLAY_NAME)));

    try {
      return OpenSearchUtil.scroll(searchRequest, UserEntity.class, openSearchClient);
    } catch (IOException e) {
      final String message =
          String.format("Exception occurred, while obtaining users: %s", e.getMessage());
      throw new TasklistRuntimeException(message, e);
    }
  }

  private ScriptSort getScriptSort(final List<String> userIds) {
    final String scriptCode =
        String.format(
            "def userIdsCount = params.userIds.size();"
                + "def userId = doc['%s'].value;"
                + "def foundIdx = params.userIds.indexOf(userId);"
                + "return foundIdx > -1 ? foundIdx: userIdsCount + 1;",
            UserIndex.USER_ID);
    return new ScriptSort.Builder()
        .type(ScriptSortType.Number)
        .script(
            s ->
                s.inline(
                    is ->
                        is.lang("painless")
                            .source(scriptCode)
                            .params("userIds", JsonData.of(userIds))))
        .order(SortOrder.Asc)
        .build();
  }
}
