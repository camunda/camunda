package io.camunda.zeebe.it.clustering;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.dockerjava.api.command.CreateContainerCmd;
import com.github.dockerjava.api.model.Capability;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.protocol.Protocol;
import io.camunda.zeebe.test.util.testcontainers.ContainerLogsDumper;
import io.zeebe.containers.ZeebeBrokerNode;
import io.zeebe.containers.ZeebeContainer;
import io.zeebe.containers.cluster.ZeebeCluster;
import java.io.IOException;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.agrona.CloseHelper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.Container.ExecResult;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.utility.DockerImageName;

public class DeployClusteredIT {

  private static final BpmnModelInstance PROCESS =
      Bpmn.createExecutableProcess("process").startEvent().endEvent().done();

  private static final Logger LOGGER = LoggerFactory.getLogger(FixedPartitionDistributionIT.class);

  private Network network;
  private ZeebeCluster cluster;

  @SuppressWarnings("unused")
  @RegisterExtension
  final ContainerLogsDumper logsWatcher =
      new ContainerLogsDumper(() -> cluster.getBrokers(), LOGGER);

  @BeforeEach
  void beforeEach() {
    network = Network.newNetwork();
  }

  @AfterEach
  void afterEach() {
    CloseHelper.quietCloseAll(cluster, network);
  }

  @Test
  void shouldDistributePartitions() throws IOException, InterruptedException {
    // given
    cluster =
        ZeebeCluster.builder()
            .withImage(DockerImageName.parse("camunda/zeebe:SNAPSHOT"))
            .withBrokersCount(3)
            .withEmbeddedGateway(true)
            .withPartitionsCount(3)
            //            .withBrokerConfig(
            //                n -> {
            //                  ((ZeebeContainer) n)
            //                      .withCreateContainerCmdModifier(
            //                          (CreateContainerCmd it) ->
            //                              it.withHostConfig(
            //
            // HostConfig.newHostConfig().withCapAdd(Capability.NET_ADMIN)));
            //                })
            .withReplicationFactor(3)
            .build();
    cluster.getBrokers().forEach((nodeId, broker) -> configureBroker(broker));
    cluster.start();

    final Set<Integer> nodes = IntStream.range(0, 3).boxed().collect(Collectors.toSet());

    final var zeebeClient = cluster.newClientBuilder().build();
    final var topology = zeebeClient.newTopologyRequest().send().join();
    final var leaderOfPartitionOne =
        topology.getBrokers().stream()
            .filter(b -> b.getPartitions().get(0).isLeader())
            .findFirst()
            .orElseThrow();

    final var leaderOfPartitionThree =
        topology.getBrokers().stream()
            .filter(b -> b.getPartitions().get(2).isLeader())
            .findFirst()
            .orElseThrow();
    assertThat(leaderOfPartitionOne.getNodeId()).isNotEqualTo(leaderOfPartitionThree.getNodeId());

    nodes.remove(leaderOfPartitionOne.getNodeId());
    nodes.remove(leaderOfPartitionThree.getNodeId());

    final ZeebeBrokerNode<? extends GenericContainer<?>> leaderOneNode =
        cluster.getBrokers().get(leaderOfPartitionOne.getNodeId());

    runCommandInContainer(leaderOneNode, "apt update");
    runCommandInContainer(leaderOneNode, "apt install -y iproute2");
    final var results =
        runCommandInContainer(leaderOneNode, "ip route").getStdout().trim().split(" ");
    final var ipAddress = results[results.length - 1];

    // alternative extract ip address
    //    final var execResult = runCommandInContainer(leaderOneNode,
    //        "ip route | grep 'src' | awk '{ print $NF }'");
    //    final var ipAddress = execResult.getStdout();
    //    LOGGER.info("Error IP {}", execResult.getStderr());
    //    LOGGER.info("Returned IP {}", ipAddress);

    final ZeebeBrokerNode<? extends GenericContainer<?>> leaderThreeNode =
        cluster.getBrokers().get(leaderOfPartitionThree.getNodeId());

    runCommandInContainer(leaderThreeNode, "apt update");
    runCommandInContainer(leaderThreeNode, "apt install -y iproute2");
    LOGGER.info(
        "{}",
        runCommandInContainer(leaderThreeNode, "ip route add unreachable " + ipAddress)
            .getStdout());

    //    LOGGER.info("{}", leaderThreeNode.execInContainer("ip", "route").getStdout());

    zeebeClient.newDeployCommand().addProcessModel(PROCESS, "process.bpmn").send().join();

    final var partitions = IntStream.range(1, 4).boxed().collect(Collectors.toSet());

    while (!partitions.isEmpty()) {
      final var processInstanceEvent =
          zeebeClient
              .newCreateInstanceCommand()
              .bpmnProcessId("process")
              .latestVersion()
              .send()
              .join();

      final var partitionId =
          Protocol.decodePartitionId(processInstanceEvent.getProcessInstanceKey());

      LOGGER.info("Instance created on partition: {}", partitionId);
      partitions.remove(partitionId);
    }
  }

  private ExecResult runCommandInContainer(
      final ZeebeBrokerNode<? extends GenericContainer<?>> container, final String command)
      throws IOException, InterruptedException {
    LOGGER.info("Run command: {}", command);

    final var commands = command.split(" ");
    final var execResult = container.execInContainer(commands);

    if (execResult.getExitCode() == 0) {
      LOGGER.info("Command {} was successful.", command);
    } else {
      LOGGER.info("Command {} failed with code: {}", command, execResult.getExitCode());
      LOGGER.info("Stderr: {}", execResult.getStderr());
    }

    return execResult;
  }

  private void configureBroker(final ZeebeBrokerNode<?> broker) {
    ((ZeebeContainer) broker)
        .withCreateContainerCmdModifier(
            (CreateContainerCmd it) ->
                it.withHostConfig(it.getHostConfig().withCapAdd(Capability.NET_ADMIN)));

    broker
        .withEnv("ZEEBE_BROKER_NETWORK_MAXMESSAGESIZE", "1MB")
        .withEnv("ZEEBE_BROKER_DATA_LOGSEGMENTSIZE", "16MB");
  }
}
