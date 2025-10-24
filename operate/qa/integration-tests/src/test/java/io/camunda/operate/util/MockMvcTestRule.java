/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.util;

import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.List;
import org.junit.rules.ExternalResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.mock.http.MockHttpOutputMessage;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.context.WebApplicationContext;

public class MockMvcTestRule extends ExternalResource {

  private static final Logger LOGGER = LoggerFactory.getLogger(MockMvcTestRule.class);

  @Autowired
  @Lazy
  @Qualifier("operateObjectMapper")
  protected ObjectMapper objectMapper;

  private MockMvc mockMvc;

  private MappingJackson2HttpMessageConverter mappingJackson2HttpMessageConverter;

  /*
   *  From spring boot documentation:
   *
   *  [...]
   *  in favor of APPLICATION_JSON since major browsers like Chrome now comply with the specification
   *  and interpret correctly UTF-8 special characters without requiring a charset=UTF-8 parameter.
   *  [...]
   */
  private final MediaType contentType =
      new MediaType(
          MediaType.APPLICATION_JSON.getType(), MediaType.APPLICATION_JSON.getSubtype()
          // ,Charset.forName("utf8")
          );
  @Autowired private WebApplicationContext webApplicationContext;

  @Override
  public void before() {
    mockMvc = webAppContextSetup(webApplicationContext).build();
    mappingJackson2HttpMessageConverter = new MappingJackson2HttpMessageConverter(objectMapper);
  }

  public MockMvc getMockMvc() {
    return mockMvc;
  }

  public ObjectMapper getObjectMapper() {
    return objectMapper;
  }

  public String json(final Object o) {
    final MockHttpOutputMessage mockHttpOutputMessage = new MockHttpOutputMessage();
    try {
      mappingJackson2HttpMessageConverter.write(
          o, MediaType.APPLICATION_JSON, mockHttpOutputMessage);
    } catch (final IOException e) {
      throw new RuntimeException(e);
    }
    return mockHttpOutputMessage.getBodyAsString();
  }

  public <T> List<T> listFromResponse(final MvcResult result, final Class<T> clazz) {
    final JavaType type = objectMapper.getTypeFactory().constructCollectionType(List.class, clazz);
    return fromResponse(result, type);
  }

  public <T> T fromResponse(final MvcResult result, final Class<T> clazz) {
    return fromResponse(result, objectMapper.getTypeFactory().constructSimpleType(clazz, null));
  }

  public <T> T fromResponse(final MvcResult result, final JavaType type) {
    try {
      return objectMapper.readValue(result.getResponse().getContentAsString(), type);
    } catch (final IOException e) {
      throw new RuntimeException(e);
    }
  }

  public <T> T fromResponse(final MvcResult result, final TypeReference<T> valueTypeRef) {
    try {
      return objectMapper.readValue(result.getResponse().getContentAsString(), valueTypeRef);
    } catch (final IOException e) {
      throw new RuntimeException(e);
    }
  }

  public MediaType getContentType() {
    return contentType;
  }
}
