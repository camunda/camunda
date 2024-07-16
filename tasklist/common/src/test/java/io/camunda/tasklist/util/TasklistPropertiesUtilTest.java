/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.util;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = SpringContextHolder.class)
public class TasklistPropertiesUtilTest {

  @AfterEach
  public void clearCache() throws Exception {
    final Field field = TasklistPropertiesUtil.class.getDeclaredField("isOpenSearchDatabaseCache");
    field.setAccessible(true);
    field.set(null, null);
  }

  @Nested
  @TestPropertySource(properties = {"camunda.tasklist.database=opensearch"})
  class OpenSearchDatabaseTests {

    @Test
    public void testIsOpenSearchDatabaseWhenTrue() {
      assertTrue(TasklistPropertiesUtil.isOpenSearchDatabase());
    }
  }

  @Nested
  @TestPropertySource(properties = {"camunda.tasklist.database=elasticsearch"})
  class ElasticSearchDatabaseTests {

    @Test
    public void testIsOpenSearchDatabaseWhenFalse() {
      assertFalse(TasklistPropertiesUtil.isOpenSearchDatabase());
    }
  }
}
