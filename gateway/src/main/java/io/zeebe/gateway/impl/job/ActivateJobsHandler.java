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
package io.zeebe.gateway.impl.job;

import io.grpc.stub.StreamObserver;
import io.zeebe.gateway.Loggers;
import io.zeebe.gateway.RequestMapper;
import io.zeebe.gateway.ResponseMapper;
import io.zeebe.gateway.impl.broker.BrokerClient;
import io.zeebe.gateway.impl.broker.request.BrokerActivateJobsRequest;
import io.zeebe.gateway.protocol.GatewayOuterClass.ActivateJobsRequest;
import io.zeebe.gateway.protocol.GatewayOuterClass.ActivateJobsResponse;
import java.util.HashMap;
import java.util.Map;

public class ActivateJobsHandler {

  private final Map<String, Integer> jobTypeToNextPartitionId = new HashMap<>();
  private final BrokerClient brokerClient;

  public ActivateJobsHandler(BrokerClient brokerClient) {
    this.brokerClient = brokerClient;
  }

  public void activateJobs(
      int partitionsCount,
      ActivateJobsRequest request,
      StreamObserver<ActivateJobsResponse> responseObserver) {
    activateJobs(
        RequestMapper.toActivateJobsRequest(request),
        partitionIdIteratorForType(request.getType(), partitionsCount),
        request.getMaxJobsToActivate(),
        request.getType(),
        responseObserver);
  }

  private void activateJobs(
      BrokerActivateJobsRequest request,
      PartitionIdIterator partitionIdIterator,
      int remainingAmount,
      String jobType,
      StreamObserver<ActivateJobsResponse> responseObserver) {
    activateJobs(request, partitionIdIterator, remainingAmount, jobType, responseObserver, false);
  }

  private void activateJobs(
      BrokerActivateJobsRequest request,
      PartitionIdIterator partitionIdIterator,
      int remainingAmount,
      String jobType,
      StreamObserver<ActivateJobsResponse> responseObserver,
      boolean pollPrevPartition) {

    if (remainingAmount > 0 && (pollPrevPartition || partitionIdIterator.hasNext())) {
      final int partitionId =
          pollPrevPartition
              ? partitionIdIterator.getCurrentPartitionId()
              : partitionIdIterator.next();

      // partitions to check and jobs to activate left
      request.setPartitionId(partitionId);
      request.setMaxJobsToActivate(remainingAmount);
      brokerClient.sendRequest(
          request,
          (key, response) -> {
            final ActivateJobsResponse grpcResponse =
                ResponseMapper.toActivateJobsResponse(key, response);
            final int jobsCount = grpcResponse.getJobsCount();
            if (jobsCount > 0) {
              responseObserver.onNext(grpcResponse);
            }

            activateJobs(
                request,
                partitionIdIterator,
                remainingAmount - jobsCount,
                jobType,
                responseObserver,
                response.getTruncated());
          },
          error -> {
            Loggers.GATEWAY_LOGGER.warn(
                "Failed to activate jobs for type {} from partition {}",
                jobType,
                partitionIdIterator.getCurrentPartitionId(),
                error);
            activateJobs(request, partitionIdIterator, remainingAmount, jobType, responseObserver);
          });
    } else {
      // enough jobs activated or no more partitions left to check
      jobTypeToNextPartitionId.put(jobType, partitionIdIterator.getCurrentPartitionId());
      responseObserver.onCompleted();
    }
  }

  private PartitionIdIterator partitionIdIteratorForType(String jobType, int partitionsCount) {
    final Integer nextPartitionId = jobTypeToNextPartitionId.computeIfAbsent(jobType, t -> 0);
    return new PartitionIdIterator(nextPartitionId, partitionsCount);
  }
}
