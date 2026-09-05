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
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequest.AddZoneRequest;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequest.BrokerScaleRequest;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequest.ClusterRestoreRequest;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequest.ClusterZoneMigrationRequest;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequest.ExportingStateChangeRequest;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequest.ForceZoneRemoveRequest;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequest.ModeChangeRequest;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequest.PurgeRequest;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequest.RemovePhysicalTenantRequest;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequest.RestoreRequest;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequest.UpdatePartitionDistributorConfigRequest;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequest.UpdateRoutingStateRequest;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequest.UpdateZonePrioritiesRequest;
import io.camunda.zeebe.dynamic.config.api.ErrorResponse;
import io.camunda.zeebe.dynamic.config.state.CurrentClusterConfiguration;
import io.camunda.zeebe.util.Either;

public interface ClusterConfigurationRequestsSerializer {

  byte[] encodeAddMembersRequest(ClusterConfigurationManagementRequest.AddMembersRequest req);

  byte[] encodeRemoveMembersRequest(ClusterConfigurationManagementRequest.RemoveMembersRequest req);

  byte[] encodeJoinPartitionRequest(ClusterConfigurationManagementRequest.JoinPartitionRequest req);

  byte[] encodeLeavePartitionRequest(
      ClusterConfigurationManagementRequest.LeavePartitionRequest req);

  byte[] encodeScaleRequest(BrokerScaleRequest scaleRequest);

  byte[] encodePurgeRequest(PurgeRequest purgeRequest);

  byte[] encodeRemovePhysicalTenantRequest(RemovePhysicalTenantRequest removePhysicalTenantRequest);

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

  byte[] encodeUpdatePartitionDistributorConfigRequest(
      UpdatePartitionDistributorConfigRequest request);

  byte[] encodeClusterZoneMigrationRequest(ClusterZoneMigrationRequest request);

  byte[] encodeForceRemoveZoneRequest(ForceZoneRemoveRequest request);

  byte[] encodeAddZoneRequest(AddZoneRequest request);

  byte[] encodeUpdateZonePrioritiesRequest(UpdateZonePrioritiesRequest request);

  ClusterConfigurationManagementRequest.AddMembersRequest decodeAddMembersRequest(
      byte[] encodedState);

  ClusterConfigurationManagementRequest.RemoveMembersRequest decodeRemoveMembersRequest(
      byte[] encodedState);

  ClusterConfigurationManagementRequest.JoinPartitionRequest decodeJoinPartitionRequest(
      byte[] encodedState);

  ClusterConfigurationManagementRequest.LeavePartitionRequest decodeLeavePartitionRequest(
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

  ClusterConfigurationManagementRequest.RemovePhysicalTenantRequest
      decodeRemovePhysicalTenantRequest(byte[] encodedRequest);

  byte[] encodeResponse(ClusterConfigurationChangeResponse response);

  byte[] encodeResponse(CurrentClusterConfiguration response);

  byte[] encodeResponse(ErrorResponse response);

  Either<ErrorResponse, ClusterConfigurationChangeResponse> decodeTopologyChangeResponse(
      byte[] encodedResponse);

  Either<ErrorResponse, CurrentClusterConfiguration> decodeClusterConfigurationResponse(
      byte[] encodedResponse);

  UpdateRoutingStateRequest decodeUpdateRoutingStateRequest(byte[] bytes);

  UpdatePartitionDistributorConfigRequest decodeUpdatePartitionDistributorConfigRequest(
      byte[] bytes);

  ClusterZoneMigrationRequest decodeClusterZoneMigrationRequest(byte[] bytes);

  ForceZoneRemoveRequest decodeForceRemoveZoneRequest(byte[] bytes);

  AddZoneRequest decodeAddZoneRequest(byte[] bytes);

  UpdateZonePrioritiesRequest decodeUpdateZonePrioritiesRequest(byte[] bytes);

  byte[] encodeModeChangeRequest(ModeChangeRequest modeChangeRequest);

  ModeChangeRequest decodeModeChangeRequest(byte[] encodedRequest);

  byte[] encodeExportingStateChangeRequest(ExportingStateChangeRequest request);

  ExportingStateChangeRequest decodeExportingStateChangeRequest(byte[] encodedRequest);

  byte[] encodeRestoreRequest(RestoreRequest request);

  RestoreRequest decodeRestoreRequest(byte[] encodedRequest);

  byte[] encodeClusterRestoreRequest(ClusterRestoreRequest request);

  ClusterRestoreRequest decodeClusterRestoreRequest(byte[] encodedRequest);
}
