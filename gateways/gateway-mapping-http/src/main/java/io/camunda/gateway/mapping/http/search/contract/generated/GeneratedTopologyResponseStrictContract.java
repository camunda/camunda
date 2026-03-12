/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mapping.http.search.contract.generated;

import io.camunda.gateway.mapping.http.search.contract.policy.ContractPolicy;
import jakarta.annotation.Generated;
import java.util.ArrayList;
import java.util.Objects;
import org.springframework.lang.Nullable;

@Generated(value = "io.camunda.gateway.mapping.http.tools.GenerateContractMappingPoc")
public record GeneratedTopologyResponseStrictContract(
    java.util.List<GeneratedBrokerInfoStrictContract> brokers,
    @Nullable String clusterId,
    Integer clusterSize,
    Integer partitionsCount,
    Integer replicationFactor,
    String gatewayVersion,
    String lastCompletedChangeId) {

  public GeneratedTopologyResponseStrictContract {
    Objects.requireNonNull(brokers, "brokers is required and must not be null");
    Objects.requireNonNull(clusterSize, "clusterSize is required and must not be null");
    Objects.requireNonNull(partitionsCount, "partitionsCount is required and must not be null");
    Objects.requireNonNull(replicationFactor, "replicationFactor is required and must not be null");
    Objects.requireNonNull(gatewayVersion, "gatewayVersion is required and must not be null");
    Objects.requireNonNull(
        lastCompletedChangeId, "lastCompletedChangeId is required and must not be null");
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

  private static <T> T applyRequiredPolicy(
      final T value,
      final ContractPolicy.FieldPolicy<T> policy,
      final ContractPolicy.FieldRef field) {
    return java.util.Objects.requireNonNull(policy, field.fieldName() + " policy must not be null")
        .apply(value, field, null);
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
    private ContractPolicy.FieldPolicy<Object> brokersPolicy;
    private String clusterId;
    private Integer clusterSize;
    private ContractPolicy.FieldPolicy<Integer> clusterSizePolicy;
    private Integer partitionsCount;
    private ContractPolicy.FieldPolicy<Integer> partitionsCountPolicy;
    private Integer replicationFactor;
    private ContractPolicy.FieldPolicy<Integer> replicationFactorPolicy;
    private String gatewayVersion;
    private ContractPolicy.FieldPolicy<String> gatewayVersionPolicy;
    private String lastCompletedChangeId;
    private ContractPolicy.FieldPolicy<String> lastCompletedChangeIdPolicy;

    private Builder() {}

    @Override
    public ClusterSizeStep brokers(
        final Object brokers, final ContractPolicy.FieldPolicy<Object> policy) {
      this.brokers = brokers;
      this.brokersPolicy = policy;
      return this;
    }

    @Override
    public PartitionsCountStep clusterSize(
        final Integer clusterSize, final ContractPolicy.FieldPolicy<Integer> policy) {
      this.clusterSize = clusterSize;
      this.clusterSizePolicy = policy;
      return this;
    }

    @Override
    public ReplicationFactorStep partitionsCount(
        final Integer partitionsCount, final ContractPolicy.FieldPolicy<Integer> policy) {
      this.partitionsCount = partitionsCount;
      this.partitionsCountPolicy = policy;
      return this;
    }

    @Override
    public GatewayVersionStep replicationFactor(
        final Integer replicationFactor, final ContractPolicy.FieldPolicy<Integer> policy) {
      this.replicationFactor = replicationFactor;
      this.replicationFactorPolicy = policy;
      return this;
    }

    @Override
    public LastCompletedChangeIdStep gatewayVersion(
        final String gatewayVersion, final ContractPolicy.FieldPolicy<String> policy) {
      this.gatewayVersion = gatewayVersion;
      this.gatewayVersionPolicy = policy;
      return this;
    }

    @Override
    public OptionalStep lastCompletedChangeId(
        final String lastCompletedChangeId, final ContractPolicy.FieldPolicy<String> policy) {
      this.lastCompletedChangeId = lastCompletedChangeId;
      this.lastCompletedChangeIdPolicy = policy;
      return this;
    }

    @Override
    public OptionalStep clusterId(final String clusterId) {
      this.clusterId = clusterId;
      return this;
    }

    @Override
    public OptionalStep clusterId(
        final String clusterId, final ContractPolicy.FieldPolicy<String> policy) {
      this.clusterId = policy.apply(clusterId, Fields.CLUSTER_ID, null);
      return this;
    }

    @Override
    public GeneratedTopologyResponseStrictContract build() {
      return new GeneratedTopologyResponseStrictContract(
          coerceBrokers(applyRequiredPolicy(this.brokers, this.brokersPolicy, Fields.BROKERS)),
          this.clusterId,
          applyRequiredPolicy(this.clusterSize, this.clusterSizePolicy, Fields.CLUSTER_SIZE),
          applyRequiredPolicy(
              this.partitionsCount, this.partitionsCountPolicy, Fields.PARTITIONS_COUNT),
          applyRequiredPolicy(
              this.replicationFactor, this.replicationFactorPolicy, Fields.REPLICATION_FACTOR),
          applyRequiredPolicy(
              this.gatewayVersion, this.gatewayVersionPolicy, Fields.GATEWAY_VERSION),
          applyRequiredPolicy(
              this.lastCompletedChangeId,
              this.lastCompletedChangeIdPolicy,
              Fields.LAST_COMPLETED_CHANGE_ID));
    }
  }

  public interface BrokersStep {
    ClusterSizeStep brokers(final Object brokers, final ContractPolicy.FieldPolicy<Object> policy);
  }

  public interface ClusterSizeStep {
    PartitionsCountStep clusterSize(
        final Integer clusterSize, final ContractPolicy.FieldPolicy<Integer> policy);
  }

  public interface PartitionsCountStep {
    ReplicationFactorStep partitionsCount(
        final Integer partitionsCount, final ContractPolicy.FieldPolicy<Integer> policy);
  }

  public interface ReplicationFactorStep {
    GatewayVersionStep replicationFactor(
        final Integer replicationFactor, final ContractPolicy.FieldPolicy<Integer> policy);
  }

  public interface GatewayVersionStep {
    LastCompletedChangeIdStep gatewayVersion(
        final String gatewayVersion, final ContractPolicy.FieldPolicy<String> policy);
  }

  public interface LastCompletedChangeIdStep {
    OptionalStep lastCompletedChangeId(
        final String lastCompletedChangeId, final ContractPolicy.FieldPolicy<String> policy);
  }

  public interface OptionalStep {
    OptionalStep clusterId(final String clusterId);

    OptionalStep clusterId(final String clusterId, final ContractPolicy.FieldPolicy<String> policy);

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
