package org.camunda.operate.rest;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Arrays;
import org.camunda.operate.rest.dto.WorkflowInstanceQueryDto;
import org.camunda.operate.util.WebSecurityDisabledConfig;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.core.IsNot;
import org.hamcrest.text.IsEmptyString;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.mock.http.MockHttpOutputMessage;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.context.WebApplicationContext;
import static org.junit.Assert.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

/**
 * @author Svetlana Dorokhova.
 */
@RunWith(SpringRunner.class)
@SpringBootTest(classes = {TestApplication.class, WebSecurityDisabledConfig.class})
@WebAppConfiguration
public class WorkflowInstanceRestServiceTest {

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

  @Before
  public void setup() throws Exception {
    this.mockMvc = webAppContextSetup(webApplicationContext).build();
  }

  @Test
  public void userNotFound() throws Exception {
    WorkflowInstanceQueryDto workflowInstanceQueryDto = new WorkflowInstanceQueryDto();
    workflowInstanceQueryDto.setRunning(true);
    workflowInstanceQueryDto.setWithIncidents(true);
    workflowInstanceQueryDto.setWithoutIncidents(true);
    mockMvc.perform(post("/workflow-instance/count/")
      .content(this.json(workflowInstanceQueryDto))
      .contentType(contentType))
      .andExpect(status().isOk())
      .andExpect(content().contentType(contentType))
      .andExpect(content().string(new IsNot<>(new IsEmptyString())));
  }

  protected String json(Object o) throws IOException {
    MockHttpOutputMessage mockHttpOutputMessage = new MockHttpOutputMessage();
    this.mappingJackson2HttpMessageConverter.write(
      o, MediaType.APPLICATION_JSON, mockHttpOutputMessage);
    return mockHttpOutputMessage.getBodyAsString();
  }

}
