/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 *
 * GENERATED FILE - DO NOT EDIT.
 * Source: zeebe/gateway-protocol/src/main/proto/v2/cluster.yaml#/components/schemas/TopologyResponse
 */
package io.camunda.gateway.mapping.http.search.contract.generated;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.camunda.gateway.mapping.http.search.contract.policy.ContractPolicy;
import jakarta.annotation.Generated;
import java.util.ArrayList;
import java.util.Objects;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@JsonInclude(JsonInclude.Include.ALWAYS)
@NullMarked
@Generated(value = "io.camunda.gateway.mapping.http.tools.GenerateContractMappingPoc")
public record GeneratedTopologyResponseStrictContract(
    @JsonProperty("brokers") java.util.List<GeneratedBrokerInfoStrictContract> brokers,
    @JsonProperty("clusterId") @Nullable String clusterId,
    @JsonProperty("clusterSize") Integer clusterSize,
    @JsonProperty("partitionsCount") Integer partitionsCount,
    @JsonProperty("replicationFactor") Integer replicationFactor,
    @JsonProperty("gatewayVersion") String gatewayVersion,
    @JsonProperty("lastCompletedChangeId") String lastCompletedChangeId) {

  public GeneratedTopologyResponseStrictContract {
    Objects.requireNonNull(brokers, "No brokers provided.");
    Objects.requireNonNull(clusterSize, "No clusterSize provided.");
    Objects.requireNonNull(partitionsCount, "No partitionsCount provided.");
    Objects.requireNonNull(replicationFactor, "No replicationFactor provided.");
    Objects.requireNonNull(gatewayVersion, "No gatewayVersion provided.");
    Objects.requireNonNull(lastCompletedChangeId, "No lastCompletedChangeId provided.");
  }

  public static java.util.List<GeneratedBrokerInfoStrictContract> coerceBrokers(
      final Object value) {
    if (value == null) {
      return null;
    }
    if (!(value instanceof java.util.List<?> listValue)) {
      throw new IllegalArgumentException(
          "brokers must be a List of GeneratedBrokerInfoStrictContract, but was "
              + value.getClass().getName());
    }

    final var result = new ArrayList<GeneratedBrokerInfoStrictContract>(listValue.size());
    for (final var item : listValue) {
      if (item == null) {
        result.add(null);
      } else if (item instanceof GeneratedBrokerInfoStrictContract strictItem) {
        result.add(strictItem);

      } else {
        throw new IllegalArgumentException(
            "brokers must contain only GeneratedBrokerInfoStrictContract items, but got "
                + item.getClass().getName());
      }
    }
    return java.util.List.copyOf(result);
  }

  public static BrokersStep builder() {
    return new Builder();
  }

  public static final class Builder
      implements BrokersStep,
          ClusterSizeStep,
          PartitionsCountStep,
          ReplicationFactorStep,
          GatewayVersionStep,
          LastCompletedChangeIdStep,
          OptionalStep {
    private Object brokers;
    private String clusterId;
    private Integer clusterSize;
    private Integer partitionsCount;
    private Integer replicationFactor;
    private String gatewayVersion;
    private String lastCompletedChangeId;

    private Builder() {}

    @Override
    public ClusterSizeStep brokers(final Object brokers) {
      this.brokers = brokers;
      return this;
    }

    @Override
    public PartitionsCountStep clusterSize(final Integer clusterSize) {
      this.clusterSize = clusterSize;
      return this;
    }

    @Override
    public ReplicationFactorStep partitionsCount(final Integer partitionsCount) {
      this.partitionsCount = partitionsCount;
      return this;
    }

    @Override
    public GatewayVersionStep replicationFactor(final Integer replicationFactor) {
      this.replicationFactor = replicationFactor;
      return this;
    }

    @Override
    public LastCompletedChangeIdStep gatewayVersion(final String gatewayVersion) {
      this.gatewayVersion = gatewayVersion;
      return this;
    }

    @Override
    public OptionalStep lastCompletedChangeId(final String lastCompletedChangeId) {
      this.lastCompletedChangeId = lastCompletedChangeId;
      return this;
    }

    @Override
    public OptionalStep clusterId(final @Nullable String clusterId) {
      this.clusterId = clusterId;
      return this;
    }

    @Override
    public OptionalStep clusterId(
        final @Nullable String clusterId, final ContractPolicy.FieldPolicy<String> policy) {
      this.clusterId = policy.apply(clusterId, Fields.CLUSTER_ID, null);
      return this;
    }

    @Override
    public GeneratedTopologyResponseStrictContract build() {
      return new GeneratedTopologyResponseStrictContract(
          coerceBrokers(this.brokers),
          this.clusterId,
          this.clusterSize,
          this.partitionsCount,
          this.replicationFactor,
          this.gatewayVersion,
          this.lastCompletedChangeId);
    }
  }

  public interface BrokersStep {
    ClusterSizeStep brokers(final Object brokers);
  }

  public interface ClusterSizeStep {
    PartitionsCountStep clusterSize(final Integer clusterSize);
  }

  public interface PartitionsCountStep {
    ReplicationFactorStep partitionsCount(final Integer partitionsCount);
  }

  public interface ReplicationFactorStep {
    GatewayVersionStep replicationFactor(final Integer replicationFactor);
  }

  public interface GatewayVersionStep {
    LastCompletedChangeIdStep gatewayVersion(final String gatewayVersion);
  }

  public interface LastCompletedChangeIdStep {
    OptionalStep lastCompletedChangeId(final String lastCompletedChangeId);
  }

  public interface OptionalStep {
    OptionalStep clusterId(final @Nullable String clusterId);

    OptionalStep clusterId(
        final @Nullable String clusterId, final ContractPolicy.FieldPolicy<String> policy);

    GeneratedTopologyResponseStrictContract build();
  }

  public static final class Fields {
    public static final ContractPolicy.FieldRef BROKERS =
        ContractPolicy.field("TopologyResponse", "brokers");
    public static final ContractPolicy.FieldRef CLUSTER_ID =
        ContractPolicy.field("TopologyResponse", "clusterId");
    public static final ContractPolicy.FieldRef CLUSTER_SIZE =
        ContractPolicy.field("TopologyResponse", "clusterSize");
    public static final ContractPolicy.FieldRef PARTITIONS_COUNT =
        ContractPolicy.field("TopologyResponse", "partitionsCount");
    public static final ContractPolicy.FieldRef REPLICATION_FACTOR =
        ContractPolicy.field("TopologyResponse", "replicationFactor");
    public static final ContractPolicy.FieldRef GATEWAY_VERSION =
        ContractPolicy.field("TopologyResponse", "gatewayVersion");
    public static final ContractPolicy.FieldRef LAST_COMPLETED_CHANGE_ID =
        ContractPolicy.field("TopologyResponse", "lastCompletedChangeId");

    private Fields() {}
  }
}
