/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.serializer;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonStreamContext;
import io.camunda.zeebe.gateway.protocol.rest.UserTaskItem;
import io.camunda.zeebe.gateway.protocol.rest.UserTaskSearchQueryResponse;
import io.camunda.zeebe.gateway.rest.RequestMapper;
import io.camunda.zeebe.util.Either.Left;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

class LongSerializerTest {

  private LongSerializer longSerializer;
  private JsonGenerator jsonGenerator;
  private JsonStreamContext context;
  private JsonStreamContext parentContext;
  private HttpServletRequest httpServletRequest;

  @BeforeEach
  void setUp() {
    longSerializer = new LongSerializer();
    jsonGenerator = mock(JsonGenerator.class);
    context = mock(JsonStreamContext.class);
    parentContext = mock(JsonStreamContext.class);
    doReturn(context).when(jsonGenerator).getOutputContext();
    doReturn(parentContext).when(context).getParent();

    httpServletRequest = mock(HttpServletRequest.class);
    RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(httpServletRequest));
  }

  @Test
  void shouldSerializeNonRestClassAsNumber() throws IOException {
    // given
    doReturn(new Left<>(false)).when(context).getCurrentValue();
    // when
    longSerializer.serialize(1L, jsonGenerator, null);
    // then
    verify(jsonGenerator).writeNumber(1L);
    verify(jsonGenerator, never()).writeString(anyString());
    verify(httpServletRequest, never()).getHeader(any());
  }

  @Test
  void shouldSerializeRestClassWithoutKeyNameAsNumber() throws IOException {
    // given
    doReturn(new UserTaskItem()).when(context).getCurrentValue();
    doReturn("field").when(context).getCurrentName();
    // when
    longSerializer.serialize(1L, jsonGenerator, null);
    // then
    verify(jsonGenerator).writeNumber(1L);
    verify(jsonGenerator, never()).writeString(anyString());
    verify(httpServletRequest, never()).getHeader(HttpHeaders.ACCEPT);
  }

  @Test
  void shouldSerializeRestClassWithDefaultStringIfNoHeaderAvailable() throws IOException {
    // given
    doReturn(new UserTaskItem()).when(context).getCurrentValue();
    doReturn("elementInstanceKey").when(context).getCurrentName();
    // when
    longSerializer.serialize(1L, jsonGenerator, null);
    // then
    verify(jsonGenerator).writeString("1");
    verify(jsonGenerator, never()).writeNumber(anyLong());
    verify(httpServletRequest).getHeader(HttpHeaders.ACCEPT);
  }

  @Test
  void shouldSerializeRestClassWithNumberIfHeaderSent() throws IOException {
    // given
    doReturn(new UserTaskItem()).when(context).getCurrentValue();
    doReturn("elementInstanceKey").when(context).getCurrentName();
    doReturn(RequestMapper.MEDIA_TYPE_KEYS_NUMBER_VALUE)
        .when(httpServletRequest)
        .getHeader(HttpHeaders.ACCEPT);
    // when
    longSerializer.serialize(1L, jsonGenerator, null);
    // then
    verify(jsonGenerator).writeNumber(1L);
    verify(jsonGenerator, never()).writeString(anyString());
    verify(httpServletRequest).getHeader(HttpHeaders.ACCEPT);
  }

  @Test
  void shouldSerializeRestClassWithStringIfHeaderSent() throws IOException {
    // given
    doReturn(new UserTaskItem()).when(context).getCurrentValue();
    doReturn("elementInstanceKey").when(context).getCurrentName();
    doReturn(RequestMapper.MEDIA_TYPE_KEYS_STRING_VALUE)
        .when(httpServletRequest)
        .getHeader(HttpHeaders.ACCEPT);
    // when
    longSerializer.serialize(1L, jsonGenerator, null);
    // then
    verify(jsonGenerator).writeString(String.valueOf(1L));
    verify(jsonGenerator, never()).writeNumber(anyLong());
    verify(httpServletRequest).getHeader(HttpHeaders.ACCEPT);
  }

  @Test
  void shouldSerializeArrayField() throws IOException {
    // given
    doReturn(1L).when(context).getCurrentValue();
    doReturn(null).when(context).getCurrentName();
    doReturn(new UserTaskSearchQueryResponse()).when(parentContext).getCurrentValue();
    doReturn("assignedMemberKeys").when(parentContext).getCurrentName();
    // when
    longSerializer.serialize(1L, jsonGenerator, null);
    // then
    verify(jsonGenerator).writeString("1");
    verify(jsonGenerator, never()).writeNumber(anyLong());
    verify(httpServletRequest).getHeader(HttpHeaders.ACCEPT);
  }

  @Test
  void shouldSerializeRestClassWithNumberIfClientIs87x() throws IOException {
    // given
    doReturn(new UserTaskItem()).when(context).getCurrentValue();
    doReturn("elementInstanceKey").when(context).getCurrentName();
    doReturn(LongSerializer.HEADER_JAVA_CLIENT_AGENT + "8.7.0")
        .when(httpServletRequest)
        .getHeader(HttpHeaders.USER_AGENT);
    // when
    longSerializer.serialize(1L, jsonGenerator, null);
    // then
    verify(jsonGenerator).writeString(String.valueOf(1L));
    verify(jsonGenerator, never()).writeNumber(anyLong());
    verify(httpServletRequest).getHeader(HttpHeaders.ACCEPT);
  }

  @Test
  void shouldSerializeRestClassWithNumberIfClientIs86x() throws IOException {
    // given
    doReturn(new UserTaskItem()).when(context).getCurrentValue();
    doReturn("elementInstanceKey").when(context).getCurrentName();
    doReturn(LongSerializer.HEADER_JAVA_CLIENT_AGENT + "8.6.9")
        .when(httpServletRequest)
        .getHeader(HttpHeaders.USER_AGENT);
    // when
    longSerializer.serialize(1L, jsonGenerator, null);
    // then
    verify(jsonGenerator).writeNumber(1L);
    verify(jsonGenerator, never()).writeString(anyString());
    verify(httpServletRequest).getHeader(HttpHeaders.ACCEPT);
  }

  @Test
  void shouldSerializeRestClassWithStringIfClientIs85x() throws IOException {
    // given
    doReturn(new UserTaskItem()).when(context).getCurrentValue();
    doReturn("elementInstanceKey").when(context).getCurrentName();
    doReturn(LongSerializer.HEADER_JAVA_CLIENT_AGENT + "8.5.0")
        .when(httpServletRequest)
        .getHeader(HttpHeaders.USER_AGENT);
    // when
    longSerializer.serialize(1L, jsonGenerator, null);
    // then
    verify(jsonGenerator).writeNumber(1L);
    verify(jsonGenerator, never()).writeString(anyString());
    verify(httpServletRequest).getHeader(HttpHeaders.ACCEPT);
  }
}
