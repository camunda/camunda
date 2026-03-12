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

@Generated(value = "io.camunda.gateway.mapping.http.tools.GenerateContractMappingPoc")
public record GeneratedBrokerInfoStrictContract(
    Integer nodeId,
    String host,
    Integer port,
    java.util.List<GeneratedPartitionStrictContract> partitions,
    String version) {

  public GeneratedBrokerInfoStrictContract {
    Objects.requireNonNull(nodeId, "nodeId is required and must not be null");
    Objects.requireNonNull(host, "host is required and must not be null");
    Objects.requireNonNull(port, "port is required and must not be null");
    Objects.requireNonNull(partitions, "partitions is required and must not be null");
    Objects.requireNonNull(version, "version is required and must not be null");
  }

  public static java.util.List<GeneratedPartitionStrictContract> coercePartitions(
      final Object value) {
    if (value == null) {
      return null;
    }
    if (!(value instanceof java.util.List<?> listValue)) {
      throw new IllegalArgumentException(
          "partitions must be a List of GeneratedPartitionStrictContract, but was "
              + value.getClass().getName());
    }

    final var result = new ArrayList<GeneratedPartitionStrictContract>(listValue.size());
    for (final var item : listValue) {
      if (item == null) {
        result.add(null);
      } else if (item instanceof GeneratedPartitionStrictContract strictItem) {
        result.add(strictItem);

      } else {
        throw new IllegalArgumentException(
            "partitions must contain only GeneratedPartitionStrictContract items, but got "
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

  public static NodeIdStep builder() {
    return new Builder();
  }

  public static final class Builder
      implements NodeIdStep, HostStep, PortStep, PartitionsStep, VersionStep, OptionalStep {
    private Integer nodeId;
    private ContractPolicy.FieldPolicy<Integer> nodeIdPolicy;
    private String host;
    private ContractPolicy.FieldPolicy<String> hostPolicy;
    private Integer port;
    private ContractPolicy.FieldPolicy<Integer> portPolicy;
    private Object partitions;
    private ContractPolicy.FieldPolicy<Object> partitionsPolicy;
    private String version;
    private ContractPolicy.FieldPolicy<String> versionPolicy;

    private Builder() {}

    @Override
    public HostStep nodeId(final Integer nodeId, final ContractPolicy.FieldPolicy<Integer> policy) {
      this.nodeId = nodeId;
      this.nodeIdPolicy = policy;
      return this;
    }

    @Override
    public PortStep host(final String host, final ContractPolicy.FieldPolicy<String> policy) {
      this.host = host;
      this.hostPolicy = policy;
      return this;
    }

    @Override
    public PartitionsStep port(
        final Integer port, final ContractPolicy.FieldPolicy<Integer> policy) {
      this.port = port;
      this.portPolicy = policy;
      return this;
    }

    @Override
    public VersionStep partitions(
        final Object partitions, final ContractPolicy.FieldPolicy<Object> policy) {
      this.partitions = partitions;
      this.partitionsPolicy = policy;
      return this;
    }

    @Override
    public OptionalStep version(
        final String version, final ContractPolicy.FieldPolicy<String> policy) {
      this.version = version;
      this.versionPolicy = policy;
      return this;
    }

    @Override
    public GeneratedBrokerInfoStrictContract build() {
      return new GeneratedBrokerInfoStrictContract(
          applyRequiredPolicy(this.nodeId, this.nodeIdPolicy, Fields.NODE_ID),
          applyRequiredPolicy(this.host, this.hostPolicy, Fields.HOST),
          applyRequiredPolicy(this.port, this.portPolicy, Fields.PORT),
          coercePartitions(
              applyRequiredPolicy(this.partitions, this.partitionsPolicy, Fields.PARTITIONS)),
          applyRequiredPolicy(this.version, this.versionPolicy, Fields.VERSION));
    }
  }

  public interface NodeIdStep {
    HostStep nodeId(final Integer nodeId, final ContractPolicy.FieldPolicy<Integer> policy);
  }

  public interface HostStep {
    PortStep host(final String host, final ContractPolicy.FieldPolicy<String> policy);
  }

  public interface PortStep {
    PartitionsStep port(final Integer port, final ContractPolicy.FieldPolicy<Integer> policy);
  }

  public interface PartitionsStep {
    VersionStep partitions(
        final Object partitions, final ContractPolicy.FieldPolicy<Object> policy);
  }

  public interface VersionStep {
    OptionalStep version(final String version, final ContractPolicy.FieldPolicy<String> policy);
  }

  public interface OptionalStep {
    GeneratedBrokerInfoStrictContract build();
  }

  public static final class Fields {
    public static final ContractPolicy.FieldRef NODE_ID =
        ContractPolicy.field("BrokerInfo", "nodeId");
    public static final ContractPolicy.FieldRef HOST = ContractPolicy.field("BrokerInfo", "host");
    public static final ContractPolicy.FieldRef PORT = ContractPolicy.field("BrokerInfo", "port");
    public static final ContractPolicy.FieldRef PARTITIONS =
        ContractPolicy.field("BrokerInfo", "partitions");
    public static final ContractPolicy.FieldRef VERSION =
        ContractPolicy.field("BrokerInfo", "version");

    private Fields() {}
  }
}
