/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.graphql;

import com.graphql.spring.boot.test.GraphQLTestTemplate;
import io.camunda.tasklist.property.TasklistProperties;
import io.camunda.tasklist.util.TasklistIntegrationTest;
import io.camunda.tasklist.util.TestApplication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(
    classes = TestApplication.class,
    properties = {TasklistProperties.PREFIX + ".graphql-introspection-enabled = true"},
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class IntrospectionEnabledIT extends TasklistIntegrationTest {

  @Autowired private GraphQLTestTemplate graphQLTestTemplate;

  @Test
  void schemaIntrospectionShouldBeEnabled() throws Exception {
    // given
    final String schemaIntrospectionQuery = "{ __schema { types { name } } }";

    // when
    final var response = graphQLTestTemplate.postMultipart(schemaIntrospectionQuery, "{}");

    // then
    response.assertThatNoErrorsArePresent();
    response.assertThatDataField().isNotNull();
  }

  @Test
  void typeIntrospectionShouldBeEnabled() throws Exception {
    // given
    final String typeIntrospectionQuery = "{ __type(name: \"Query\") { name fields { name } } }";

    // when
    final var response = graphQLTestTemplate.postMultipart(typeIntrospectionQuery, "{}");

    // then
    response.assertThatNoErrorsArePresent();
    response.assertThatDataField().isNotNull();
  }
}
