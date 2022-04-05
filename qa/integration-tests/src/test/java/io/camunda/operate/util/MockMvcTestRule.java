/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.util;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.List;

import org.junit.rules.ExternalResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.mock.http.MockHttpOutputMessage;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.context.WebApplicationContext;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import static org.junit.Assert.assertNotNull;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

public class MockMvcTestRule extends ExternalResource {

  private static final Logger logger = LoggerFactory.getLogger(MockMvcTestRule.class);

  @Autowired
  protected ObjectMapper objectMapper ;

  private MockMvc mockMvc;

  private HttpMessageConverter mappingJackson2HttpMessageConverter;

  /*
   *  From spring boot documentation:
   *  
   *  [...] 
   *  in favor of APPLICATION_JSON since major browsers like Chrome now comply with the specification 
   *  and interpret correctly UTF-8 special characters without requiring a charset=UTF-8 parameter.
   *  [...]
   */
  private MediaType contentType = new MediaType(MediaType.APPLICATION_JSON.getType(),
                                                MediaType.APPLICATION_JSON.getSubtype()
                                                //,Charset.forName("utf8")                                      
                                  );
  
  @Autowired
  void setConverters(HttpMessageConverter<?>[] converters) {

    this.mappingJackson2HttpMessageConverter = Arrays.asList(converters).stream()
      .filter(hmc -> hmc instanceof MappingJackson2HttpMessageConverter)
      .findAny()
      .orElse(null);

    assertNotNull("the JSON message converter must not be null",
      this.mappingJackson2HttpMessageConverter);
  }

  @Autowired
  private WebApplicationContext webApplicationContext;

  @Override
  public void before() {
    this.mockMvc = webAppContextSetup(webApplicationContext).build();
  }

  public MockMvc getMockMvc() {
    return mockMvc;
  }

  public ObjectMapper getObjectMapper() {
    return objectMapper;
  }


  public String json(Object o) {
    MockHttpOutputMessage mockHttpOutputMessage = new MockHttpOutputMessage();
    try {
      mappingJackson2HttpMessageConverter.write(
        o, MediaType.APPLICATION_JSON, mockHttpOutputMessage);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
    return mockHttpOutputMessage.getBodyAsString();
  }

  public <T> List<T> listFromResponse(MvcResult result, Class<T> clazz) {
    JavaType type = objectMapper.getTypeFactory().
      constructCollectionType(List.class, clazz);
    return fromResponse(result, type);
  }
  
  public <T> T fromResponse(MvcResult result, Class<T> clazz) {
    return fromResponse(result, objectMapper.getTypeFactory().constructSimpleType(clazz, null));
  }

  public <T> T fromResponse(MvcResult result, JavaType type) {
    try {
      return objectMapper.readValue(result.getResponse().getContentAsString(), type);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public <T> T fromResponse(MvcResult result, TypeReference<T> valueTypeRef) {
    try {
      return objectMapper.readValue(result.getResponse().getContentAsString(), valueTypeRef);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public MediaType getContentType() {
    return contentType;
  }
}
