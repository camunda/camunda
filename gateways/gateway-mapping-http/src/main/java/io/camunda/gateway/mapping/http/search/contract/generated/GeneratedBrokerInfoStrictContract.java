/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 *
 * GENERATED FILE - DO NOT EDIT.
 * Source: zeebe/gateway-protocol/src/main/proto/v2/cluster.yaml#/components/schemas/BrokerInfo
 */
package io.camunda.gateway.mapping.http.search.contract.generated;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.camunda.gateway.mapping.http.search.contract.policy.ContractPolicy;
import io.camunda.gateway.mapping.http.util.KeyUtil;
import jakarta.annotation.Generated;
import java.util.ArrayList;
import java.util.Objects;
import org.jspecify.annotations.NullMarked;


@JsonInclude(JsonInclude.Include.ALWAYS)
@NullMarked
@Generated(value = "io.camunda.gateway.mapping.http.tools.GenerateContractMappingPoc")
public record GeneratedBrokerInfoStrictContract(
    Integer nodeId,
    String host,
    Integer port,
    java.util.List<GeneratedPartitionStrictContract> partitions,
    String version
) {

  public GeneratedBrokerInfoStrictContract {
    Objects.requireNonNull(nodeId, "nodeId is required and must not be null");
    Objects.requireNonNull(host, "host is required and must not be null");
    Objects.requireNonNull(port, "port is required and must not be null");
    Objects.requireNonNull(partitions, "partitions is required and must not be null");
    Objects.requireNonNull(version, "version is required and must not be null");
  }

  public static java.util.List<GeneratedPartitionStrictContract> coercePartitions(final Object value) {
    if (value == null) {
      return null;
    }
    if (!(value instanceof java.util.List<?> listValue)) {
      throw new IllegalArgumentException(
          "partitions must be a List of GeneratedPartitionStrictContract, but was " + value.getClass().getName());
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



  public static NodeIdStep builder() {
    return new Builder();
  }

  public static final class Builder implements NodeIdStep, HostStep, PortStep, PartitionsStep, VersionStep, OptionalStep {
    private Integer nodeId;
    private String host;
    private Integer port;
    private Object partitions;
    private String version;

    private Builder() {}

    @Override
    public HostStep nodeId(final Integer nodeId) {
      this.nodeId = nodeId;
      return this;
    }

    @Override
    public PortStep host(final String host) {
      this.host = host;
      return this;
    }

    @Override
    public PartitionsStep port(final Integer port) {
      this.port = port;
      return this;
    }

    @Override
    public VersionStep partitions(final Object partitions) {
      this.partitions = partitions;
      return this;
    }

    @Override
    public OptionalStep version(final String version) {
      this.version = version;
      return this;
    }
    @Override
    public GeneratedBrokerInfoStrictContract build() {
      return new GeneratedBrokerInfoStrictContract(
          this.nodeId,
          this.host,
          this.port,
          coercePartitions(this.partitions),
          this.version);
    }
  }

  public interface NodeIdStep {
    HostStep nodeId(final Integer nodeId);
  }

  public interface HostStep {
    PortStep host(final String host);
  }

  public interface PortStep {
    PartitionsStep port(final Integer port);
  }

  public interface PartitionsStep {
    VersionStep partitions(final Object partitions);
  }

  public interface VersionStep {
    OptionalStep version(final String version);
  }

  public interface OptionalStep {
    GeneratedBrokerInfoStrictContract build();
  }


  public static final class Fields {
    public static final ContractPolicy.FieldRef NODE_ID = ContractPolicy.field("BrokerInfo", "nodeId");
    public static final ContractPolicy.FieldRef HOST = ContractPolicy.field("BrokerInfo", "host");
    public static final ContractPolicy.FieldRef PORT = ContractPolicy.field("BrokerInfo", "port");
    public static final ContractPolicy.FieldRef PARTITIONS = ContractPolicy.field("BrokerInfo", "partitions");
    public static final ContractPolicy.FieldRef VERSION = ContractPolicy.field("BrokerInfo", "version");

    private Fields() {}
  }


}
