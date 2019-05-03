/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.util;

import static org.assertj.core.api.Assertions.*;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Set;

import org.camunda.operate.exceptions.OperateRuntimeException;
import org.junit.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

import static org.camunda.operate.util.CollectionUtil.*;

public class CollectionUtilTest {

  @Test
  public void testAsMapOneEntry() {
    Map<String,Object> result = asMap("key1","value1");
    assertThat(result).hasSize(1);
    assertThat(result).containsEntry("key1", "value1");
  }
  
  @Test
  public void testAsMapManyEntries() {
    Map<String,Object> result = asMap("key1","value1","key2","value2","key3","value3");
    assertThat(result).hasSize(3);
    assertThat(result).containsEntry("key2", "value2");
    assertThat(result).containsEntry("key3", "value3");
  }
  
  @Test
  public void testAsMapException() {
    assertThatExceptionOfType(OperateRuntimeException.class).isThrownBy(() -> asMap((Object[])null)); 
    assertThatExceptionOfType(OperateRuntimeException.class).isThrownBy(() -> asMap("key1"));
    assertThatExceptionOfType(OperateRuntimeException.class).isThrownBy(() -> asMap("key1","value1","key2")); 
  }
  
  @Test
  public void testAsSet() {
    Set<String> result = asSet("entry1");
    assertThat(result).hasSize(1);
    assertThat(result).contains("entry1");
    result = asSet("entry1","entry2","entry3");
    assertThat(result).hasSize(3);
    assertThat(result).contains("entry1","entry2","entry3");
    result = asSet("entry1",null,"entry3");
    assertThat(result).hasSize(2);
    assertThat(result).contains("entry1","entry3");
  }

  @Test
  public void testToJSONMap() throws IOException {
    ObjectMapper objectMapper = new ObjectMapper();
    OffsetDateTime dateTime = OffsetDateTime.now();
    assertThat(toJSONMap(objectMapper, asMap("key","value"))).containsEntry("key", "value");
    Map<String,Object> dateAsMap = (Map<String, Object>) toJSONMap(objectMapper, asMap("date",dateTime)).get("date");
    assertThat(dateAsMap).containsKey("year");
  }
}
