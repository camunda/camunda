/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.store.opensearch;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.operate.OperateProfileService;
import io.camunda.operate.conditions.OpensearchCondition;
import io.camunda.operate.entities.UserEntity;
import io.camunda.operate.exceptions.OperateRuntimeException;
import io.camunda.operate.store.NotFoundException;
import io.camunda.operate.store.UserStore;
import io.camunda.webapps.schema.descriptors.index.OperateUserIndex;
import java.io.IOException;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch._types.FieldValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Conditional(OpensearchCondition.class)
@Component
@DependsOn("searchEngineSchemaInitializer")
@Profile(
    "!"
        + OperateProfileService.LDAP_AUTH_PROFILE
        + " & !"
        + OperateProfileService.SSO_AUTH_PROFILE
        + " & !"
        + OperateProfileService.IDENTITY_AUTH_PROFILE)
public class OpensearchUserStore implements UserStore {

  private static final Logger LOGGER = LoggerFactory.getLogger(OpensearchUserStore.class);

  @Autowired protected OpenSearchClient openSearchClient;

  @Autowired
  @Qualifier("operateObjectMapper")
  protected ObjectMapper objectMapper;

  @Autowired private OperateUserIndex operateUserIndex;

  protected String userEntityToJSONString(final UserEntity aUser) throws JsonProcessingException {
    return objectMapper.writeValueAsString(aUser);
  }

  @Override
  public UserEntity getById(final String id) {
    try {
      final var response =
          openSearchClient.search(
              r ->
                  r.index(operateUserIndex.getAlias())
                      .query(
                          q ->
                              q.term(
                                  t -> t.field(OperateUserIndex.USER_ID).value(FieldValue.of(id)))),
              UserEntity.class);
      final var hits = response.hits().total().value();
      if (hits == 1) {
        return response.hits().hits().get(0).source();
      } else if (hits > 1) {
        throw new NotFoundException(
            String.format("Could not find unique user with userId '%s'.", id));
      } else {
        throw new NotFoundException(String.format("Could not find user with userId '%s'.", id));
      }
    } catch (final IOException e) {
      final String message =
          String.format("Exception occurred, while obtaining the user: %s", e.getMessage());
      throw new OperateRuntimeException(message, e);
    }
  }

  @Override
  public void save(final UserEntity user) {
    try {
      final var response =
          openSearchClient.index(
              r ->
                  r.index(operateUserIndex.getFullQualifiedName()).id(user.getId()).document(user));
      LOGGER.info("User {} {}", user.getUserId(), response.result());
    } catch (final Exception t) {
      LOGGER.error("Could not create user with userId {}", user.getUserId(), t);
    }
  }
}
