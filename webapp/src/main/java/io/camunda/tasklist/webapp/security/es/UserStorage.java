/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package io.camunda.tasklist.webapp.security.es;

import static io.camunda.tasklist.webapp.security.TasklistURIs.SSO_AUTH_PROFILE;
import static org.elasticsearch.index.query.QueryBuilders.constantScoreQuery;
import static org.elasticsearch.index.query.QueryBuilders.idsQuery;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.camunda.tasklist.entities.UserEntity;
import io.camunda.tasklist.exceptions.TasklistRuntimeException;
import io.camunda.tasklist.schema.indices.UserIndex;
import io.camunda.tasklist.util.ElasticsearchUtil;
import io.camunda.tasklist.webapp.es.reader.AbstractReader;
import io.camunda.tasklist.webapp.rest.exception.NotFoundException;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.common.xcontent.XContentType;
import org.elasticsearch.index.query.ConstantScoreQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.script.Script;
import org.elasticsearch.script.ScriptType;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.sort.ScriptSortBuilder.ScriptSortType;
import org.elasticsearch.search.sort.SortBuilders;
import org.elasticsearch.search.sort.SortOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("!" + SSO_AUTH_PROFILE)
@DependsOn("schemaStartup")
public class UserStorage extends AbstractReader {

  private static final Logger LOGGER = LoggerFactory.getLogger(UserStorage.class);

  private static final XContentType XCONTENT_TYPE = XContentType.JSON;

  @Autowired private UserIndex userIndex;

  public UserEntity getByName(String username) {
    final SearchRequest searchRequest =
        new SearchRequest(userIndex.getAlias())
            .source(
                new SearchSourceBuilder()
                    .query(QueryBuilders.termQuery(UserIndex.USERNAME, username)));
    try {
      final SearchResponse response = esClient.search(searchRequest, RequestOptions.DEFAULT);
      if (response.getHits().getTotalHits().value == 1) {
        return ElasticsearchUtil.fromSearchHit(
            response.getHits().getHits()[0].getSourceAsString(), objectMapper, UserEntity.class);
      } else if (response.getHits().getTotalHits().value > 1) {
        throw new NotFoundException(
            String.format("Could not find unique user with username '%s'.", username));
      } else {
        throw new NotFoundException(
            String.format("Could not find user with username '%s'.", username));
      }
    } catch (IOException e) {
      final String message =
          String.format("Exception occurred, while obtaining the user: %s", e.getMessage());
      throw new TasklistRuntimeException(message, e);
    }
  }

  public void create(UserEntity user) {
    try {
      final IndexRequest request =
          new IndexRequest(userIndex.getFullQualifiedName())
              .id(user.getId())
              .source(userEntityToJSONString(user), XCONTENT_TYPE);
      esClient.index(request, RequestOptions.DEFAULT);
    } catch (Exception e) {
      LOGGER.error("Could not create user with username {}", user.getUsername(), e);
    }
  }

  public List<UserEntity> getUsersByUsernames(List<String> usernames) {
    final ConstantScoreQueryBuilder esQuery =
        constantScoreQuery(idsQuery().addIds(usernames.toArray(String[]::new)));

    final SearchRequest searchRequest =
        new SearchRequest(userIndex.getAlias())
            .source(
                new SearchSourceBuilder()
                    .query(esQuery)
                    .sort(
                        SortBuilders.scriptSort(getScript(usernames), ScriptSortType.NUMBER)
                            .order(SortOrder.ASC))
                    .fetchSource(
                        new String[] {UserIndex.USERNAME, UserIndex.FIRSTNAME, UserIndex.LASTNAME},
                        null));

    try {
      return ElasticsearchUtil.scroll(searchRequest, UserEntity.class, objectMapper, esClient);
    } catch (IOException e) {
      final String message =
          String.format("Exception occurred, while obtaining users: %s", e.getMessage());
      throw new TasklistRuntimeException(message, e);
    }
  }

  private Script getScript(final List<String> usernames) {
    final String scriptCode =
        String.format(
            "def usernamesCount = params.usernames.size();"
                + "def username = doc['%s'].value;"
                + "def foundIdx = params.usernames.indexOf(username);"
                + "return foundIdx > -1 ? foundIdx: usernamesCount + 1;",
            UserIndex.USERNAME);
    return new Script(
        ScriptType.INLINE, Script.DEFAULT_SCRIPT_LANG, scriptCode, Map.of("usernames", usernames));
  }

  protected String userEntityToJSONString(UserEntity aUser) throws JsonProcessingException {
    return objectMapper.writeValueAsString(aUser);
  }
}
