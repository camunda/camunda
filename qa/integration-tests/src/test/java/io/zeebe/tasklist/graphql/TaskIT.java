/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package io.zeebe.tasklist.graphql;

import java.io.IOException;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import com.graphql.spring.boot.test.GraphQLResponse;
import com.graphql.spring.boot.test.GraphQLTestTemplate;
import io.zeebe.tasklist.util.TasklistZeebeIntegrationTest;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class TaskIT extends TasklistZeebeIntegrationTest {

  @Autowired
  private GraphQLTestTemplate graphQLTestTemplate;

  @Test
  public void getTasksTest() throws IOException {
    final GraphQLResponse response = graphQLTestTemplate.postForResource("graphql/get-tasks.graphql");
    assertTrue(response.isOk());
    assertEquals("123", response.get("$.data.tasks[0].key"));

  }

}
