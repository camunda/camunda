/*
 * Zeebe Broker Core
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package io.zeebe.broker.workflow.map;

import static io.zeebe.protocol.Protocol.DEPLOYMENT_PARTITION;

import io.zeebe.broker.Loggers;
import io.zeebe.broker.clustering.base.topology.NodeInfo;
import io.zeebe.broker.clustering.base.topology.PartitionInfo;
import io.zeebe.broker.clustering.base.topology.TopologyManager;
import io.zeebe.broker.clustering.base.topology.TopologyPartitionListener;
import io.zeebe.broker.system.workflow.repository.api.management.FetchWorkflowRequest;
import io.zeebe.broker.system.workflow.repository.api.management.FetchWorkflowResponse;
import io.zeebe.broker.workflow.model.ExecutableWorkflow;
import io.zeebe.broker.workflow.model.transformation.BpmnTransformer;
import io.zeebe.clustering.management.FetchWorkflowResponseDecoder;
import io.zeebe.model.bpmn.Bpmn;
import io.zeebe.model.bpmn.BpmnModelInstance;
import io.zeebe.transport.ClientResponse;
import io.zeebe.transport.ClientTransport;
import io.zeebe.util.buffer.BufferUtil;
import io.zeebe.util.sched.clock.ActorClock;
import io.zeebe.util.sched.future.ActorFuture;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.agrona.DirectBuffer;
import org.agrona.collections.Int2ObjectHashMap;
import org.agrona.collections.Long2ObjectHashMap;
import org.agrona.io.DirectBufferInputStream;

public class WorkflowCache implements TopologyPartitionListener {
  public static final long LATEST_VERSION_REFRESH_INTERVAL = Duration.ofSeconds(10).toMillis();

  private static final Duration FETCH_WORKFLOW_TIMEOUT = Duration.ofSeconds(30);

  private final FetchWorkflowRequest fetchRequest = new FetchWorkflowRequest();
  private final FetchWorkflowResponse fetchRespose = new FetchWorkflowResponse();

  private final Long2ObjectHashMap<DeployedWorkflow> workflowsByKey = new Long2ObjectHashMap<>();
  private final Map<DirectBuffer, Int2ObjectHashMap<DeployedWorkflow>>
      workflowsByProcessIdAndVersion = new HashMap<>();
  private final Map<DirectBuffer, DeployedWorkflow> latestWorkflowsByProcessId = new HashMap<>();

  private final ClientTransport clientTransport;
  private final TopologyManager topologyManager;

  private final BpmnTransformer transformer = new BpmnTransformer();

  private volatile Integer defaultTopicLeaderId;

  public WorkflowCache(ClientTransport clientTransport, TopologyManager topologyManager) {
    this.clientTransport = clientTransport;
    this.topologyManager = topologyManager;
    topologyManager.addTopologyPartitionListener(this);
  }

  public void close() {
    topologyManager.removeTopologyPartitionListener(this);
  }

  public ActorFuture<ClientResponse> fetchWorkflowByKey(long key) {
    fetchRequest.reset().workflowKey(key);

    return clientTransport
        .getOutput()
        .sendRequestWithRetry(
            this::systemTopicLeader, this::checkResponse, fetchRequest, FETCH_WORKFLOW_TIMEOUT);
  }

  public ActorFuture<ClientResponse> fetchLatestWorkflowByBpmnProcessId(
      DirectBuffer bpmnProcessId) {
    fetchRequest.reset().latestVersion().bpmnProcessId(bpmnProcessId);

    return clientTransport
        .getOutput()
        .sendRequestWithRetry(
            this::systemTopicLeader, this::checkResponse, fetchRequest, FETCH_WORKFLOW_TIMEOUT);
  }

  public ActorFuture<ClientResponse> fetchWorkflowByBpmnProcessIdAndVersion(
      DirectBuffer bpmnProcessId, int version) {
    fetchRequest.reset().version(version).bpmnProcessId(bpmnProcessId);

    return clientTransport
        .getOutput()
        .sendRequestWithRetry(
            this::systemTopicLeader, this::checkResponse, fetchRequest, FETCH_WORKFLOW_TIMEOUT);
  }

  private boolean checkResponse(DirectBuffer responseBuffer) {
    return !fetchRespose.tryWrap(responseBuffer, 0, responseBuffer.capacity());
  }

  public DeployedWorkflow addWorkflow(DirectBuffer response) {
    final long now = ActorClock.currentTimeMillis();

    fetchRespose.wrap(response, 0, response.capacity());

    final long key = fetchRespose.getWorkflowKey();

    final DeployedWorkflow existing = workflowsByKey.get(key);
    DeployedWorkflow deployedWorkflow = null;

    if (existing != null) {
      existing.setFetched(now);
      deployedWorkflow = existing;
    } else {
      if (key != FetchWorkflowResponseDecoder.workflowKeyNullValue()) {
        final DirectBuffer bpmnProcessId = fetchRespose.bpmnProcessId();
        final DirectBuffer bpmnXml = fetchRespose.getBpmnXml();
        final int version = fetchRespose.getVersion();

        // TODO: pull these things apart
        // TODO: may wanna catch exceptions
        final BpmnModelInstance modelInstance =
            Bpmn.readModelFromStream(new DirectBufferInputStream(bpmnXml));

        // TODO: do design time and runtime validation

        final List<ExecutableWorkflow> definitions =
            transformer.transformDefinitions(modelInstance);

        final ExecutableWorkflow workflow =
            definitions
                .stream()
                .filter((w) -> BufferUtil.equals(bpmnProcessId, w.getId()))
                .findFirst()
                .get();

        deployedWorkflow = new DeployedWorkflow(workflow, key, version, now);

        workflowsByKey.put(key, deployedWorkflow);

        Int2ObjectHashMap<DeployedWorkflow> versionMap =
            workflowsByProcessIdAndVersion.get(bpmnProcessId);

        if (versionMap == null) {
          versionMap = new Int2ObjectHashMap<>();
          workflowsByProcessIdAndVersion.put(bpmnProcessId, versionMap);
        }

        versionMap.put(version, deployedWorkflow);

        final DeployedWorkflow latestVersion = latestWorkflowsByProcessId.get(bpmnProcessId);
        if (latestVersion == null || latestVersion.getVersion() < version) {
          latestWorkflowsByProcessId.put(bpmnProcessId, deployedWorkflow);
        }
      }
    }

    return deployedWorkflow;
  }

  private Integer systemTopicLeader() {
    return defaultTopicLeaderId;
  }

  public DeployedWorkflow getLatestWorkflowVersionByProcessId(DirectBuffer processId) {
    final DeployedWorkflow latest = latestWorkflowsByProcessId.get(processId);

    if (latest != null) {
      final long now = ActorClock.currentTimeMillis();

      if (now - latest.getFetched() > LATEST_VERSION_REFRESH_INTERVAL) {
        // refresh latest version
        Loggers.WORKFLOW_REPOSITORY_LOGGER.debug("fetch latest version");
        return null;
      }
    }

    return latest;
  }

  public DeployedWorkflow getWorkflowByProcessIdAndVersion(DirectBuffer processId, int version) {
    final Int2ObjectHashMap<DeployedWorkflow> versionMap =
        workflowsByProcessIdAndVersion.get(processId);

    if (versionMap != null) {
      return versionMap.get(version);
    } else {
      return null;
    }
  }

  public DeployedWorkflow getWorkflowByKey(long key) {
    return workflowsByKey.get(key);
  }

  @Override
  public void onPartitionUpdated(PartitionInfo partitionInfo, NodeInfo member) {
    final Integer currentLeader = defaultTopicLeaderId;

    if (partitionInfo.getPartitionId() == DEPLOYMENT_PARTITION) {
      if (member.getLeaders().contains(partitionInfo)) {
        if (currentLeader == null || !currentLeader.equals(member.getNodeId())) {
          defaultTopicLeaderId = member.getNodeId();
        }
      }
    }
  }
}
