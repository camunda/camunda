/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.store.opensearch;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.operate.OperateProfileService;
import io.camunda.operate.conditions.OpensearchCondition;
import io.camunda.operate.entities.UserEntity;
import io.camunda.operate.exceptions.OperateRuntimeException;
import io.camunda.operate.schema.indices.UserIndex;
import io.camunda.operate.store.NotFoundException;
import io.camunda.operate.store.UserStore;
import java.io.IOException;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch._types.FieldValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Conditional(OpensearchCondition.class)
@Component
@DependsOn("schemaStartup")
@Profile(
    "!"
        + OperateProfileService.LDAP_AUTH_PROFILE
        + " & !"
        + OperateProfileService.SSO_AUTH_PROFILE
        + " & !"
        + OperateProfileService.IDENTITY_AUTH_PROFILE)
public class OpensearchUserStore implements UserStore {

  private static final Logger logger = LoggerFactory.getLogger(OpensearchUserStore.class);

  @Autowired protected OpenSearchClient openSearchClient;

  @Autowired protected ObjectMapper objectMapper;

  @Autowired private UserIndex userIndex;

  protected String userEntityToJSONString(UserEntity aUser) throws JsonProcessingException {
    return objectMapper.writeValueAsString(aUser);
  }

  @Override
  public UserEntity getById(String id) {
    try {
      var response =
          openSearchClient.search(
              r ->
                  r.index(userIndex.getAlias())
                      .query(q -> q.term(t -> t.field(UserIndex.USER_ID).value(FieldValue.of(id)))),
              UserEntity.class);
      var hits = response.hits().total().value();
      if (hits == 1) {
        return response.hits().hits().get(0).source();
      } else if (hits > 1) {
        throw new NotFoundException(
            String.format("Could not find unique user with userId '%s'.", id));
      } else {
        throw new NotFoundException(String.format("Could not find user with userId '%s'.", id));
      }
    } catch (IOException e) {
      final String message =
          String.format("Exception occurred, while obtaining the user: %s", e.getMessage());
      throw new OperateRuntimeException(message, e);
    }
  }

  @Override
  public void save(UserEntity user) {
    try {
      var response =
          openSearchClient.index(
              r -> r.index(userIndex.getFullQualifiedName()).id(user.getId()).document(user));
      logger.info("User {} {}", user.getUserId(), response.result());
    } catch (Exception t) {
      logger.error("Could not create user with userId {}", user.getUserId(), t);
    }
  }
}
