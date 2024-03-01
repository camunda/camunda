/*
 * Copyright Camunda Services GmbH
 *
 * BY INSTALLING, DOWNLOADING, ACCESSING, USING, OR DISTRIBUTING THE SOFTWARE, YOU INDICATE YOUR ACCEPTANCE TO AND ARE ENTERING INTO A CONTRACT WITH, THE LICENSOR ON THE TERMS SET OUT IN THIS AGREEMENT. IF YOU DO NOT AGREE TO THESE TERMS, YOU MUST NOT USE THE SOFTWARE. IF YOU ARE RECEIVING THE SOFTWARE ON BEHALF OF A LEGAL ENTITY, YOU REPRESENT AND WARRANT THAT YOU HAVE THE ACTUAL AUTHORITY TO AGREE TO THE TERMS AND CONDITIONS OF THIS AGREEMENT ON BEHALF OF SUCH ENTITY.
 * “Licensee” means you, an individual, or the entity on whose behalf you receive the Software.
 *
 * Permission is hereby granted, free of charge, to the Licensee obtaining a copy of this Software and associated documentation files to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject in each case to the following conditions:
 * Condition 1: If the Licensee distributes the Software or any derivative works of the Software, the Licensee must attach this Agreement.
 * Condition 2: Without limiting other conditions in this Agreement, the grant of rights is solely for non-production use as defined below.
 * "Non-production use" means any use of the Software that is not directly related to creating products, services, or systems that generate revenue or other direct or indirect economic benefits.  Examples of permitted non-production use include personal use, educational use, research, and development. Examples of prohibited production use include, without limitation, use for commercial, for-profit, or publicly accessible systems or use for commercial or revenue-generating purposes.
 *
 * If the Licensee is in breach of the Conditions, this Agreement, including the rights granted under it, will automatically terminate with immediate effect.
 *
 * SUBJECT AS SET OUT BELOW, THE SOFTWARE IS PROVIDED “AS IS”, WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE, AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 * NOTHING IN THIS AGREEMENT EXCLUDES OR RESTRICTS A PARTY’S LIABILITY FOR (A) DEATH OR PERSONAL INJURY CAUSED BY THAT PARTY’S NEGLIGENCE, (B) FRAUD, OR (C) ANY OTHER LIABILITY TO THE EXTENT THAT IT CANNOT BE LAWFULLY EXCLUDED OR RESTRICTED.
 */
package io.camunda.operate.util;

import static org.junit.Assert.assertNotNull;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
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

public class MockMvcTestRule extends ExternalResource {

  private static final Logger logger = LoggerFactory.getLogger(MockMvcTestRule.class);

  @Autowired protected ObjectMapper objectMapper;

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
  private MediaType contentType =
      new MediaType(
          MediaType.APPLICATION_JSON.getType(), MediaType.APPLICATION_JSON.getSubtype()
          // ,Charset.forName("utf8")
          );
  @Autowired private WebApplicationContext webApplicationContext;

  @Autowired
  void setConverters(HttpMessageConverter<?>[] converters) {

    this.mappingJackson2HttpMessageConverter =
        Arrays.asList(converters).stream()
            .filter(hmc -> hmc instanceof MappingJackson2HttpMessageConverter)
            .findAny()
            .orElse(null);

    assertNotNull(
        "the JSON message converter must not be null", this.mappingJackson2HttpMessageConverter);
  }

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
    JavaType type = objectMapper.getTypeFactory().constructCollectionType(List.class, clazz);
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
