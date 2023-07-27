/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.it.management;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import io.camunda.zeebe.broker.StandaloneBroker;
import io.camunda.zeebe.broker.system.configuration.BrokerCfg;
import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.it.smoke.CollectorRegistryInitializer;
import io.camunda.zeebe.it.smoke.RandomPortInitializer;
import io.camunda.zeebe.qa.util.actuator.JobStreamActuator;
import io.camunda.zeebe.shared.management.JobStreamEndpoint.RemoteJobStream;
import io.camunda.zeebe.shared.management.JobStreamEndpoint.RemoteStreamId;
import java.time.Duration;
import org.agrona.CloseHelper;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

@ExtendWith(SpringExtension.class)
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT, classes = StandaloneBroker.class)
@ContextConfiguration(
    initializers = {RandomPortInitializer.class, CollectorRegistryInitializer.class})
@ActiveProfiles("test")
public class JobStreamEndpointIT {
  @Autowired private BrokerCfg config;

  @SuppressWarnings("unused")
  @LocalServerPort
  private int managementPort;

  private ZeebeClient client;
  private JobStreamActuator actuator;

  @BeforeEach
  void beforeEach() {
    await("until broker is ready").untilAsserted(this::assertBrokerIsReady);

    client =
        ZeebeClient.newClientBuilder()
            .usePlaintext()
            .gatewayAddress(
                config.getGateway().getNetwork().getHost()
                    + ":"
                    + config.getGateway().getNetwork().getPort())
            .build();
    actuator =
        JobStreamActuator.of("http://localhost:%d/actuator/jobstreams".formatted(managementPort));
  }

  @AfterEach
  void afterEach() {
    CloseHelper.quietClose(client);

    // avoid flakiness between tests by waiting until the registries are empty
    Awaitility.await("until no streams are registered")
        .untilAsserted(
            () -> {
              final var streams = actuator.list();
              assertThat(streams.remote()).isEmpty();
              assertThat(streams.client()).isEmpty();
            });
  }

  @Test
  void shouldListMultipleRemoteStreams() {
    // given
    client
        .newStreamJobsCommand()
        .jobType("foo")
        .consumer(ignored -> {})
        .workerName("foo")
        .timeout(Duration.ofMillis(100))
        .fetchVariables("foo", "fooz")
        .send();
    client
        .newStreamJobsCommand()
        .jobType("bar")
        .consumer(ignored -> {})
        .workerName("bar")
        .timeout(Duration.ofMillis(250))
        .fetchVariables("bar", "barz")
        .send();

    // when
    final var streams =
        Awaitility.await("until all streams are registered")
            .until(actuator::listRemote, list -> list.size() == 2);

    // then
    assertThat(streams)
        .anySatisfy(
            stream -> {
              assertThat(stream.jobType()).isEqualTo("foo");
              assertThat(stream.metadata().worker()).isEqualTo("foo");
              assertThat(stream.metadata().timeout()).isEqualTo(Duration.ofMillis(100L));
              assertThat(stream.metadata().fetchVariables())
                  .containsExactlyInAnyOrder("foo", "fooz");
            })
        .anySatisfy(
            stream -> {
              assertThat(stream.jobType()).isEqualTo("bar");
              assertThat(stream.metadata().worker()).isEqualTo("bar");
              assertThat(stream.metadata().timeout()).isEqualTo(Duration.ofMillis(250));
              assertThat(stream.metadata().fetchVariables())
                  .containsExactlyInAnyOrder("bar", "barz");
            });
  }

  @Test
  void shouldListMultipleRemoteConsumers() {
    // given
    client
        .newStreamJobsCommand()
        .jobType("foo")
        .consumer(ignored -> {})
        .workerName("foo")
        .timeout(Duration.ofMillis(100))
        .fetchVariables("foo", "fooz")
        .send();
    client
        .newStreamJobsCommand()
        .jobType("foo")
        .consumer(ignored -> {})
        .workerName("foo")
        .timeout(Duration.ofMillis(100))
        .fetchVariables("foo", "fooz")
        .send();

    // when
    final var streams =
        Awaitility.await("until all streams are registered")
            .atMost(Duration.ofSeconds(60))
            .until(
                actuator::listRemote,
                list -> list.size() == 1 && list.get(0).consumers().size() == 2);

    // then
    assertThat(streams)
        .first(InstanceOfAssertFactories.type(RemoteJobStream.class))
        .extracting(RemoteJobStream::consumers)
        .asInstanceOf(InstanceOfAssertFactories.list(RemoteStreamId.class))
        .extracting(RemoteStreamId::receiver)
        .containsExactly("0", "0");
  }

  @Test
  void shouldListMultipleClientStreams() {
    // given
    client
        .newStreamJobsCommand()
        .jobType("foo")
        .consumer(ignored -> {})
        .workerName("foo")
        .timeout(Duration.ofMillis(100))
        .fetchVariables("foo", "fooz")
        .send();
    client
        .newStreamJobsCommand()
        .jobType("bar")
        .consumer(ignored -> {})
        .workerName("bar")
        .timeout(Duration.ofMillis(250))
        .fetchVariables("bar", "barz")
        .send();

    // when
    final var streams =
        Awaitility.await("until all streams are registered")
            .until(actuator::listClient, list -> list.size() == 2);

    // then
    assertThat(streams)
        .anySatisfy(
            stream -> {
              assertThat(stream.jobType()).isEqualTo("foo");
              assertThat(stream.metadata().worker()).isEqualTo("foo");
              assertThat(stream.metadata().timeout()).isEqualTo(Duration.ofMillis(100));
              assertThat(stream.metadata().fetchVariables())
                  .containsExactlyInAnyOrder("foo", "fooz");
              assertThat(stream.id()).isNotNull();
            })
        .anySatisfy(
            stream -> {
              assertThat(stream.jobType()).isEqualTo("bar");
              assertThat(stream.metadata().worker()).isEqualTo("bar");
              assertThat(stream.metadata().timeout()).isEqualTo(Duration.ofMillis(250));
              assertThat(stream.metadata().fetchVariables())
                  .containsExactlyInAnyOrder("bar", "barz");
              assertThat(stream.id()).isNotNull();
            });
  }

  private void assertBrokerIsReady() {
    given().port(managementPort).when().get("/ready").then().statusCode(204);
  }
}
