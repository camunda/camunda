/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.graphql;

import com.graphql.spring.boot.test.GraphQLTestTemplate;
import io.camunda.tasklist.util.TasklistIntegrationTest;
import org.assertj.core.api.Condition;
import org.assertj.core.data.Index;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

public class IntrospectionDisabledIT extends TasklistIntegrationTest {

  @Autowired private GraphQLTestTemplate graphQLTestTemplate;

  @Test
  void schemaIntrospectionShouldBeDisabled() throws Exception {
    // given
    final String schemaIntrospectionQuery = "{ __schema { types { name } } }";

    // when
    final var response = graphQLTestTemplate.postMultipart(schemaIntrospectionQuery, "{}");

    // then
    response
        .assertThatListOfErrors()
        .has(
            new Condition<>(e -> "GraphQL introspection is disabled".equals(e.getMessage()), null),
            Index.atIndex(0));
    response.assertThatDataField().isNull();
  }

  @Test
  void typeIntrospectionShouldBeDisabled() throws Exception {
    // given
    final String typeIntrospectionQuery = "{ __type(name: \"Query\") { name fields { name } } }";

    // when
    final var response = graphQLTestTemplate.postMultipart(typeIntrospectionQuery, "{}");

    // then
    response
        .assertThatListOfErrors()
        .has(
            new Condition<>(e -> "GraphQL introspection is disabled".equals(e.getMessage()), null),
            Index.atIndex(0));
    response.assertThatDataField().isNull();
  }
}
