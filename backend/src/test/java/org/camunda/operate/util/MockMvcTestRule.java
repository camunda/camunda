/* Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.camunda.operate.util;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.List;
import org.junit.rules.ExternalResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
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

  private Logger logger = LoggerFactory.getLogger(MockMvcTestRule.class);

  @Autowired
  @Qualifier("esObjectMapper")
  protected ObjectMapper objectMapper ;

  private MockMvc mockMvc;

  private HttpMessageConverter mappingJackson2HttpMessageConverter;

  private MediaType contentType = new MediaType(MediaType.APPLICATION_JSON.getType(),
    MediaType.APPLICATION_JSON.getSubtype(),
    Charset.forName("utf8"));

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
