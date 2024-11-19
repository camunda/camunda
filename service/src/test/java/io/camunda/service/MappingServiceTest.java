/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.search.clients.MappingSearchClient;
import io.camunda.security.auth.Authentication;
import io.camunda.service.MappingServices.MappingDTO;
import io.camunda.service.security.SecurityContextProvider;
import io.camunda.zeebe.gateway.api.util.StubbedBrokerClient;
import io.camunda.zeebe.gateway.impl.broker.request.BrokerMappingCreateRequest;
import io.camunda.zeebe.protocol.impl.record.value.authorization.MappingRecord;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.MappingIntent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class MappingServiceTest {

  private MappingServices services;
  private MappingSearchClient client;
  private Authentication authentication;
  private StubbedBrokerClient stubbedBrokerClient;

  @BeforeEach
  public void before() {
    authentication = Authentication.of(builder -> builder.user(1234L).token("auth_token"));
    stubbedBrokerClient = new StubbedBrokerClient();
    client = mock(MappingSearchClient.class);
    when(client.withSecurityContext(any())).thenReturn(client);
    services =
        new MappingServices(
            stubbedBrokerClient, mock(SecurityContextProvider.class), client, authentication);
  }

  @Test
  public void shouldCreateMapping() {
    // given
    final var mappingDTO = new MappingDTO(100L, "newClaimName", "newClaimValue");

    // when
    services.createMapping(mappingDTO);

    // then
    final BrokerMappingCreateRequest request = stubbedBrokerClient.getSingleBrokerRequest();
    assertThat(request.getIntent()).isEqualTo(MappingIntent.CREATE);
    assertThat(request.getValueType()).isEqualTo(ValueType.MAPPING);
    final MappingRecord brokerRequestValue = request.getRequestWriter();
    assertThat(brokerRequestValue.getMappingKey()).isEqualTo(mappingDTO.mappingKey());
    assertThat(brokerRequestValue.getClaimName()).isEqualTo(mappingDTO.claimName());
    assertThat(brokerRequestValue.getClaimValue()).isEqualTo(mappingDTO.claimValue());
  }
}
