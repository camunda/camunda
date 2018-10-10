/*
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.zeebe.gateway;

import static io.zeebe.util.buffer.BufferUtil.bufferAsString;

import com.google.protobuf.Empty;
import io.zeebe.gateway.cmd.ClientException;
import io.zeebe.gateway.protocol.GatewayOuterClass.BrokerInfo;
import io.zeebe.gateway.protocol.GatewayOuterClass.BrokerInfo.Builder;
import io.zeebe.gateway.protocol.GatewayOuterClass.CancelWorkflowInstanceResponse;
import io.zeebe.gateway.protocol.GatewayOuterClass.CompleteJobResponse;
import io.zeebe.gateway.protocol.GatewayOuterClass.CreateJobResponse;
import io.zeebe.gateway.protocol.GatewayOuterClass.CreateWorkflowInstanceResponse;
import io.zeebe.gateway.protocol.GatewayOuterClass.DeployWorkflowResponse;
import io.zeebe.gateway.protocol.GatewayOuterClass.FailJobResponse;
import io.zeebe.gateway.protocol.GatewayOuterClass.GetWorkflowResponse;
import io.zeebe.gateway.protocol.GatewayOuterClass.HealthResponse;
import io.zeebe.gateway.protocol.GatewayOuterClass.ListWorkflowsResponse;
import io.zeebe.gateway.protocol.GatewayOuterClass.Partition;
import io.zeebe.gateway.protocol.GatewayOuterClass.Partition.PartitionBrokerRole;
import io.zeebe.gateway.protocol.GatewayOuterClass.UpdateJobRetriesResponse;
import io.zeebe.gateway.protocol.GatewayOuterClass.UpdateWorkflowInstancePayloadResponse;
import io.zeebe.protocol.impl.data.cluster.TopologyResponseDto;
import io.zeebe.protocol.impl.data.cluster.TopologyResponseDto.BrokerDto;
import io.zeebe.protocol.impl.data.cluster.TopologyResponseDto.PartitionDto;
import io.zeebe.protocol.impl.data.repository.WorkflowMetadataAndResource;
import io.zeebe.protocol.impl.record.value.deployment.DeploymentRecord;
import io.zeebe.protocol.impl.record.value.job.JobRecord;
import io.zeebe.protocol.impl.record.value.workflowinstance.WorkflowInstanceRecord;
import java.util.ArrayList;

public class ResponseMapper {

  private static PartitionBrokerRole remapPartitionBrokerRoleEnum(
      final BrokerDto brokerDto, final PartitionDto partition) {
    switch (partition.getState()) {
      case LEADER:
        return PartitionBrokerRole.LEADER;
      case FOLLOWER:
        return PartitionBrokerRole.FOLLOW;
      default:
        throw new ClientException(
            "Unknown broker role in response for partition "
                + partition
                + " on broker "
                + brokerDto);
    }
  }

  public static HealthResponse toHealthResponse(long key, TopologyResponseDto brokerResponse) {
    final HealthResponse.Builder healthResponseBuilder = HealthResponse.newBuilder();
    final ArrayList<BrokerInfo> infos = new ArrayList<>();

    brokerResponse
        .brokers()
        .forEach(
            broker -> {
              final Builder brokerInfo = BrokerInfo.newBuilder();
              brokerInfo.setHost(bufferAsString(broker.getHost()));
              brokerInfo.setPort(broker.getPort());

              broker
                  .partitionStates()
                  .forEach(
                      partition -> {
                        final Partition.Builder partitionBuilder = Partition.newBuilder();
                        partitionBuilder.setPartitionId(partition.getPartitionId());
                        partitionBuilder.setRole(remapPartitionBrokerRoleEnum(broker, partition));
                        brokerInfo.addPartitions(partitionBuilder);
                      });

              infos.add(brokerInfo.build());
            });

    healthResponseBuilder.addAllBrokers(infos);
    return healthResponseBuilder.build();
  }

  public static DeployWorkflowResponse toDeployWorkflowResponse(
      long key, DeploymentRecord brokerResponse) {
    final DeployWorkflowResponse.Builder responseBuilder =
        DeployWorkflowResponse.newBuilder().setKey(key);

    brokerResponse
        .workflows()
        .forEach(
            workflow ->
                responseBuilder
                    .addWorkflowsBuilder()
                    .setBpmnProcessId(bufferAsString(workflow.getBpmnProcessId()))
                    .setVersion(workflow.getVersion())
                    .setWorkflowKey(workflow.getKey())
                    .setResourceName(bufferAsString(workflow.getResourceName())));

    return responseBuilder.build();
  }

  public static Empty emptyResponse(long key, Object brokerResponse) {
    return Empty.getDefaultInstance();
  }

  public static CreateJobResponse toCreateJobResponse(long key, JobRecord brokerResponse) {
    return CreateJobResponse.newBuilder().setKey(key).build();
  }

  public static UpdateJobRetriesResponse toUpdateJobRetriesResponse(
      long key, JobRecord brokerResponse) {
    return UpdateJobRetriesResponse.getDefaultInstance();
  }

  public static FailJobResponse toFailJobResponse(long key, JobRecord brokerResponse) {
    return FailJobResponse.getDefaultInstance();
  }

  public static CompleteJobResponse toCompleteJobResponse(long key, JobRecord brokerResponse) {
    return CompleteJobResponse.newBuilder().build();
  }

  public static CreateWorkflowInstanceResponse toCreateWorkflowInstanceResponse(
      long key, WorkflowInstanceRecord brokerResponse) {
    return CreateWorkflowInstanceResponse.newBuilder()
        .setWorkflowKey(brokerResponse.getWorkflowKey())
        .setBpmnProcessId(bufferAsString(brokerResponse.getBpmnProcessId()))
        .setVersion(brokerResponse.getVersion())
        .setWorkflowInstanceKey(brokerResponse.getWorkflowInstanceKey())
        .build();
  }

  public static CancelWorkflowInstanceResponse toCancelWorkflowInstanceResponse(
      long key, WorkflowInstanceRecord brokerResponse) {
    return CancelWorkflowInstanceResponse.newBuilder().build();
  }

  public static UpdateWorkflowInstancePayloadResponse toUpdateWorkflowInstancePayloadResponse(
      long key, WorkflowInstanceRecord brokerResponse) {
    return UpdateWorkflowInstancePayloadResponse.newBuilder().build();
  }

  public static ListWorkflowsResponse toListWorkflowsResponse(
      long key, io.zeebe.protocol.impl.data.repository.ListWorkflowsResponse brokerResponse) {
    final ListWorkflowsResponse.Builder builder = ListWorkflowsResponse.newBuilder();
    brokerResponse
        .getWorkflows()
        .forEach(
            workflowMetadata -> {
              builder
                  .addWorkflowsBuilder()
                  .setBpmnProcessId(bufferAsString(workflowMetadata.getBpmnProcessId()))
                  .setVersion(workflowMetadata.getVersion())
                  .setWorkflowKey(workflowMetadata.getWorkflowKey())
                  .setResourceName(bufferAsString(workflowMetadata.getResourceName()))
                  .build();
            });

    return builder.build();
  }

  public static GetWorkflowResponse toGetWorkflowResponse(
      long key, WorkflowMetadataAndResource brokerResponse) {
    return GetWorkflowResponse.newBuilder()
        .setBpmnProcessId(bufferAsString(brokerResponse.getBpmnProcessId()))
        .setVersion(brokerResponse.getVersion())
        .setWorkflowKey(brokerResponse.getWorkflowKey())
        .setResourceName(bufferAsString(brokerResponse.getResourceName()))
        .setBpmnXml(bufferAsString(brokerResponse.getBpmnXml()))
        .build();
  }

  @FunctionalInterface
  public interface BrokerResponseMapper<BrokerResponseDto, GrpcResponse> {
    GrpcResponse apply(long key, BrokerResponseDto responseDto);
  }
}
