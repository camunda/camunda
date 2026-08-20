/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.cluster.startup;

import static io.camunda.application.commons.security.CamundaSecurityConfiguration.AUTHORIZATION_CHECKS_ENV_VAR;
import static io.camunda.application.commons.security.CamundaSecurityConfiguration.UNPROTECTED_API_ENV_VAR;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.dockerjava.api.command.CreateContainerCmd;
import io.camunda.client.CamundaClient;
import io.camunda.client.api.response.ProcessInstanceResult;
import io.camunda.configuration.SecondaryStorage.SecondaryStorageType;
import io.camunda.container.CamundaContainer.BrokerContainer;
import io.camunda.container.CamundaContainer.GatewayContainer;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.qa.util.testcontainers.ZeebeTestContainerDefaults;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Tests a deployment of one standalone broker and gateway with varying docker container config. */
@Testcontainers
public class NonDefaultContainerSetupTest {

  private static Stream<Arguments> containerModifiers() {
    return Stream.of(
        /* Tests running with the default unprivileged user. */
        Arguments.arguments(
            Named.of("user[1001]", (Consumer<CreateContainerCmd>) cmd -> cmd.withUser("1001"))),
        /*
         * Runs with a random uid and guid of 0 as common for Openshift.
         * While this cannot guarantee OpenShift compatibility, it's a common compatibility issue.
         *
         * <p>See <a
         * href="https://docs.openshift.com/container-platform/latest/openshift_images/create-images.html">here</a>
         * for more.
         *
         * <p>From their docs: By default, OpenShift Container Platform runs containers using an
         * arbitrarily assigned user ID. This provides additional security against processes escaping the
         * container due to a container engine vulnerability and thereby achieving escalated permissions
         * on the host node. For an image to support running as an arbitrary user, directories and files
         * that are written to by processes in the image must be owned by the root group and be
         * read/writable by that group. Files to be executed must also have "group" execute permissions.
         *
         * <p>You can read more about UIDs/GIDs <a
         * href="https://cloud.redhat.com/blog/a-guide-to-openshift-and-uids">here</a>.
         */
        Arguments.arguments(
            Named.of(
                "user[1000620000:0]",
                (Consumer<CreateContainerCmd>) cmd -> cmd.withUser("1000620000:0"))),
        /* Tests running with a read only root file system. */
        Arguments.arguments(
            Named.of(
                "readOnlyRootFileSystem[true]",
                (Consumer<CreateContainerCmd>)
                    cmd -> Objects.requireNonNull(cmd.getHostConfig()).withReadonlyRootfs(true))));
  }

  // Use env var configuration since on CI a RO_BIND from the host filesystem is not possible
  // in order to mount the exported unified config file
  @ParameterizedTest
  @MethodSource("containerModifiers")
  void runWithContainerSetup(final Consumer<CreateContainerCmd> containerModifier) {
    try (final BrokerContainer broker =
            new BrokerContainer(ZeebeTestContainerDefaults.defaultTestImage())
                .withReadOnlyFileSystem()
                .withEnv("CAMUNDA_DATA_SECONDARYSTORAGE_TYPE", SecondaryStorageType.none.name())
                .withEnv(UNPROTECTED_API_ENV_VAR, "true")
                .withEnv(AUTHORIZATION_CHECKS_ENV_VAR, "false")
                .withCreateContainerCmdModifier(containerModifier);
        final GatewayContainer gateway =
            new GatewayContainer(ZeebeTestContainerDefaults.defaultTestImage())
                .withReadOnlyFileSystem()
                .withNetwork(broker.getNetwork())
                .withEnv("CAMUNDA_DATA_SECONDARYSTORAGE_TYPE", SecondaryStorageType.none.name())
                .withEnv(
                    "ZEEBE_GATEWAY_CLUSTER_INITIALCONTACTPOINTS",
                    broker.getInternalClusterAddress())
                .withCreateContainerCmdModifier(containerModifier)
                .dependsOn(broker)
                .withEnv(UNPROTECTED_API_ENV_VAR, "true")
                .withEnv(AUTHORIZATION_CHECKS_ENV_VAR, "false")) {
      // given
      broker.start();
      gateway.start();
      final var process = Bpmn.createExecutableProcess("process").startEvent().endEvent().done();
      final ProcessInstanceResult result;
      try (final CamundaClient client =
          CamundaClient.newClientBuilder()
              .preferRestOverGrpc(false)
              .grpcAddress(gateway.getGrpcAddress())
              .build()) {
        // when
        client
            .newDeployResourceCommand()
            .addProcessModel(process, "process.bpmn")
            .send()
            .join(10, TimeUnit.SECONDS);
        result =
            client
                .newCreateInstanceCommand()
                .bpmnProcessId("process")
                .latestVersion()
                .withResult()
                .send()
                .join(10, TimeUnit.SECONDS);
      }

      // then
      assertThat(result)
          .isNotNull()
          .extracting(ProcessInstanceResult::getBpmnProcessId)
          .isEqualTo("process");
    }
  }
}
