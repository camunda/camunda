/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config.serializer;

import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationChangeResponse;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequest;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequest.BrokerScaleRequest;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequest.PurgeRequest;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequest.UpdateRoutingStateRequest;
import io.camunda.zeebe.dynamic.config.api.ErrorResponse;
import io.camunda.zeebe.dynamic.config.state.ClusterConfiguration;
import io.camunda.zeebe.util.Either;

public interface ClusterConfigurationRequestsSerializer {

  byte[] encodeAddMembersRequest(ClusterConfigurationManagementRequest.AddMembersRequest req);

  byte[] encodeRemoveMembersRequest(ClusterConfigurationManagementRequest.RemoveMembersRequest req);

  byte[] encodeJoinPartitionRequest(ClusterConfigurationManagementRequest.JoinPartitionRequest req);

  byte[] encodeLeavePartitionRequest(
      ClusterConfigurationManagementRequest.LeavePartitionRequest req);

  byte[] encodeReassignPartitionsRequest(
      ClusterConfigurationManagementRequest.ReassignPartitionsRequest reassignPartitionsRequest);

  byte[] encodeScaleRequest(BrokerScaleRequest scaleRequest);

  byte[] encodePurgeRequest(PurgeRequest purgeRequest);

  byte[] encodeCancelChangeRequest(
      ClusterConfigurationManagementRequest.CancelChangeRequest cancelChangeRequest);

  byte[] encodeExporterDisableRequest(
      ClusterConfigurationManagementRequest.ExporterDisableRequest exporterDisableRequest);

  byte[] encodeExporterDeleteRequest(
      ClusterConfigurationManagementRequest.ExporterDeleteRequest exporterDeleteRequest);

  byte[] encodeExporterEnableRequest(
      ClusterConfigurationManagementRequest.ExporterEnableRequest exporterEnableRequest);

  byte[] encodeClusterScaleRequest(
      ClusterConfigurationManagementRequest.ClusterScaleRequest clusterScaleRequest);

  byte[] encodeClusterPatchRequest(
      ClusterConfigurationManagementRequest.ClusterPatchRequest clusterPatchRequest);

  byte[] encodeForceRemoveBrokersRequest(
      ClusterConfigurationManagementRequest.ForceRemoveBrokersRequest forceRemoveBrokersRequest);

  byte[] encodeUpdateRoutingStateRequest(UpdateRoutingStateRequest updateRoutingStateRequest);

  ClusterConfigurationManagementRequest.AddMembersRequest decodeAddMembersRequest(
      byte[] encodedState);

  ClusterConfigurationManagementRequest.RemoveMembersRequest decodeRemoveMembersRequest(
      byte[] encodedState);

  ClusterConfigurationManagementRequest.JoinPartitionRequest decodeJoinPartitionRequest(
      byte[] encodedState);

  ClusterConfigurationManagementRequest.LeavePartitionRequest decodeLeavePartitionRequest(
      byte[] encodedState);

  ClusterConfigurationManagementRequest.ReassignPartitionsRequest decodeReassignPartitionsRequest(
      byte[] encodedState);

  BrokerScaleRequest decodeScaleRequest(byte[] encodedState);

  ClusterConfigurationManagementRequest.CancelChangeRequest decodeCancelChangeRequest(
      byte[] encodedState);

  ClusterConfigurationManagementRequest.ExporterDisableRequest decodeExporterDisableRequest(
      byte[] encodedRequest);

  ClusterConfigurationManagementRequest.ExporterDeleteRequest decodeExporterDeleteRequest(
      byte[] encodedRequest);

  ClusterConfigurationManagementRequest.ExporterEnableRequest decodeExporterEnableRequest(
      byte[] encodedRequest);

  ClusterConfigurationManagementRequest.ClusterScaleRequest decodeClusterScaleRequest(
      byte[] encodedRequest);

  ClusterConfigurationManagementRequest.ClusterPatchRequest decodeClusterPatchRequest(
      byte[] encodedRequest);

  ClusterConfigurationManagementRequest.ForceRemoveBrokersRequest decodeForceRemoveBrokersRequest(
      byte[] encodedRequest);

  ClusterConfigurationManagementRequest.PurgeRequest decodePurgeRequest(byte[] encodedRequest);

  byte[] encodeResponse(ClusterConfigurationChangeResponse response);

  byte[] encodeResponse(ClusterConfiguration response);

  byte[] encodeResponse(ErrorResponse response);

  Either<ErrorResponse, ClusterConfigurationChangeResponse> decodeTopologyChangeResponse(
      byte[] encodedResponse);

  Either<ErrorResponse, ClusterConfiguration> decodeClusterTopologyResponse(byte[] encodedResponse);

  UpdateRoutingStateRequest decodeUpdateRoutingStateRequest(byte[] bytes);
}
