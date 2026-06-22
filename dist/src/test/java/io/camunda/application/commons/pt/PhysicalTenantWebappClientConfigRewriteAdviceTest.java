/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.pt;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.spring.utils.PhysicalTenantContext;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class PhysicalTenantWebappClientConfigRewriteAdviceTest {

  private static final String OPERATE_BODY =
      "window.clientConfig = {\"isEnterprise\":true,\"contextPath\":\"\",\"baseName\":\"/operate\"};";
  private static final String TASKLIST_BODY =
      "window.clientConfig = {\"contextPath\":\"\",\"baseName\":\"/tasklist\",\"clientMode\":\"v2\"};";

  private final PhysicalTenantWebappClientConfigRewriteAdvice advice =
      new PhysicalTenantWebappClientConfigRewriteAdvice();

  @Test
  void shouldSupportClientConfigRestServiceGetClientConfig() throws NoSuchMethodException {
    // given
    final MethodParameter returnType = clientConfigReturnType();

    // when / then
    assertThat(advice.supports(returnType, stringConverter())).isTrue();
  }

  @Test
  void shouldNotSupportOtherEndpoints() throws NoSuchMethodException {
    // given
    final Method method = OtherRestService.class.getDeclaredMethod("somethingElse");
    final MethodParameter returnType = new MethodParameter(method, -1);

    // when / then
    assertThat(advice.supports(returnType, stringConverter())).isFalse();
  }

  @Test
  void shouldPrefixContextPathAndBaseNameForPhysicalTenantRequest() throws NoSuchMethodException {
    // given
    final MockHttpServletRequest httpRequest = new MockHttpServletRequest();
    PhysicalTenantContext.setPhysicalTenantId(httpRequest, "tenanta");

    // when
    final String result = rewrite(OPERATE_BODY, httpRequest);

    // then
    assertThat(result)
        .contains("\"contextPath\":\"/physical-tenants/tenanta\"")
        .contains("\"baseName\":\"/physical-tenants/tenanta/operate\"");
  }

  @Test
  void shouldPrefixTasklistBaseNameForPhysicalTenantRequest() throws NoSuchMethodException {
    // given
    final MockHttpServletRequest httpRequest = new MockHttpServletRequest();
    PhysicalTenantContext.setPhysicalTenantId(httpRequest, "default");

    // when
    final String result = rewrite(TASKLIST_BODY, httpRequest);

    // then
    assertThat(result)
        .contains("\"contextPath\":\"/physical-tenants/default\"")
        .contains("\"baseName\":\"/physical-tenants/default/tasklist\"")
        .contains("\"clientMode\":\"v2\"");
  }

  @Test
  void shouldNotChangeBodyForClusterRequest() throws NoSuchMethodException {
    // given — no physical tenant id stamped on the request
    final MockHttpServletRequest httpRequest = new MockHttpServletRequest();

    // when
    final String result = rewrite(OPERATE_BODY, httpRequest);

    // then
    assertThat(result).isEqualTo(OPERATE_BODY);
  }

  private String rewrite(final String body, final MockHttpServletRequest httpRequest)
      throws NoSuchMethodException {
    return advice.beforeBodyWrite(
        body,
        clientConfigReturnType(),
        MediaType.valueOf("text/javascript"),
        StringHttpMessageConverter.class,
        new ServletServerHttpRequest(httpRequest),
        new ServletServerHttpResponse(new MockHttpServletResponse()));
  }

  private static MethodParameter clientConfigReturnType() throws NoSuchMethodException {
    final Method method = ClientConfigRestService.class.getDeclaredMethod("getClientConfig");
    return new MethodParameter(method, -1);
  }

  @SuppressWarnings("unchecked")
  private static Class<? extends HttpMessageConverter<?>> stringConverter() {
    return (Class<? extends HttpMessageConverter<?>>) (Class<?>) StringHttpMessageConverter.class;
  }

  /** Stand-in matching the operate/tasklist {@code ClientConfigRestService#getClientConfig}. */
  private static final class ClientConfigRestService {
    @SuppressWarnings("unused")
    String getClientConfig() {
      return "";
    }
  }

  private static final class OtherRestService {
    @SuppressWarnings("unused")
    String somethingElse() {
      return "";
    }
  }
}
