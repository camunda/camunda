package io.camunda.zeebe.gateway.rest.controller;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.camunda.service.FormServices;
import io.camunda.service.entities.FormEntity;
import io.camunda.service.exception.NotFoundException;
import io.camunda.service.security.auth.Authentication;
import io.camunda.zeebe.gateway.rest.RestControllerTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(value = FormController.class, properties = "camunda.rest.query.enabled=true")
public class FormControllerTest extends RestControllerTest {

  private static final Long VALID_FORM_KEY = 1L;
  private static final Long INVALID_FORM_KEY = 999L;
  private static final String FORM_ITEM_JSON =
      """
      {
        "key": 1,
        "tenantId": "tenant-1",
        "bpmnId": "bpmn-1",
        "schema": "schema",
        "version": 1
      }
      """;

  @Autowired private MockMvc mockMvc;

  @MockBean private FormServices formServices;

  @MockBean private Authentication authentication;

  @BeforeEach
  void setup() {
    when(formServices.getByKey(VALID_FORM_KEY))
        .thenReturn(new FormEntity("1", "tenant-1", "bpmn-1", "schema", 1L));

    when(formServices.getByKey(INVALID_FORM_KEY))
        .thenThrow(new NotFoundException("Form not found"));

    when(formServices.withAuthentication(authentication)).thenReturn(formServices);
  }

  @Test
  public void shouldReturnFormItemForValidFormKey() throws Exception {
    mockMvc
        .perform(get("/v2/forms/{formKey}", VALID_FORM_KEY).contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(content().json(FORM_ITEM_JSON));

    verify(formServices, times(1)).getByKey(VALID_FORM_KEY);
  }

  @Test
  public void shouldReturn404ForInvalidFormKey() throws Exception {
    mockMvc
        .perform(
            get("/v2/forms/{formKey}", INVALID_FORM_KEY).contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isNotFound())
        .andExpect(
            content()
                .json(
                    """
                {
                  "type": "about:blank",
                  "title": "NOT_FOUND",
                  "status": 404,
                  "detail": "Form not found"
                }
                """));

    verify(formServices, times(1)).getByKey(INVALID_FORM_KEY);
  }

  @Test
  public void shouldReturn500OnUnexpectedException() throws Exception {
    when(formServices.getByKey(VALID_FORM_KEY)).thenThrow(new RuntimeException("Unexpected error"));

    mockMvc
        .perform(get("/v2/forms/{formKey}", VALID_FORM_KEY).contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isInternalServerError())
        .andExpect(
            content()
                .json(
                    """
                    {
                        "type":"about:blank",
                        "title":"Failed to execute Get Form by key.",
                        "status":500,
                        "detail":"Unexpected error",
                         "instance":"/v2/forms/1"}
                    """));

    verify(formServices, times(1)).getByKey(VALID_FORM_KEY);
  }
}
