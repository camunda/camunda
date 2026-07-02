/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.gateway;

import com.google.protobuf.util.JsonFormat;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.protobuf.ProtobufHttpMessageConverter;
import org.springframework.http.converter.protobuf.ProtobufJsonFormatHttpMessageConverter;

/**
 * Registers the two Spring converters that let every coordination/admin endpoint speak both
 * protobuf binary and JSON for the generated message types, so Spring performs the content
 * negotiation:
 *
 * <ul>
 *   <li>{@link ProtobufHttpMessageConverter} — {@code application/x-protobuf} binary, read/written
 *       via {@code Message.parseFrom}/{@code toByteArray}, used by the client SDK.
 *   <li>{@link ProtobufJsonFormatHttpMessageConverter} — {@code application/json} via {@link
 *       JsonFormat}, used by humans (Postman/curl). The JSON shape is the protobuf-canonical shape.
 * </ul>
 *
 * <p>Both are ordinary message converters; declaring them as beans lets Spring Boot prepend them to
 * the converter list so a controller annotated {@code produces}/{@code consumes} = {@code
 * {application/json, application/x-protobuf}} negotiates the right representation from the
 * request's {@code Accept}/{@code Content-Type} headers.
 */
@Configuration(proxyBeanMethods = false)
public class ProtobufConfiguration {

  /** Binary protobuf representation ({@code application/x-protobuf}). */
  @Bean
  public ProtobufHttpMessageConverter protobufHttpMessageConverter() {
    return new ProtobufHttpMessageConverter();
  }

  /** JSON representation of protobuf messages via {@link JsonFormat} ({@code application/json}). */
  @Bean
  public ProtobufJsonFormatHttpMessageConverter protobufJsonFormatHttpMessageConverter() {
    return new ProtobufJsonFormatHttpMessageConverter(
        JsonFormat.parser().ignoringUnknownFields(), JsonFormat.printer());
  }
}
