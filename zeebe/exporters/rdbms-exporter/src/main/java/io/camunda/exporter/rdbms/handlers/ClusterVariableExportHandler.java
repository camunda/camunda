/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.rdbms.handlers;

import static io.camunda.zeebe.protocol.record.value.ClusterVariableScope.GLOBAL;

import io.camunda.db.rdbms.write.domain.ClusterVariableDbModel;
import io.camunda.db.rdbms.write.service.ClusterVariableWriter;
import io.camunda.exporter.rdbms.RdbmsExportHandler;
import io.camunda.search.entities.ClusterVariableScope;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.ClusterVariableIntent;
import io.camunda.zeebe.protocol.record.value.ClusterVariableRecordValue;

public class ClusterVariableExportHandler
    implements RdbmsExportHandler<ClusterVariableRecordValue> {

  private final ClusterVariableWriter clusterVariableWriter;

  public ClusterVariableExportHandler(final ClusterVariableWriter clusterVariableWriter) {
    this.clusterVariableWriter = clusterVariableWriter;
  }

  @Override
  public boolean canExport(final Record<ClusterVariableRecordValue> record) {
    return record.getValueType() == ValueType.CLUSTER_VARIABLE
        && (record.getIntent() == ClusterVariableIntent.CREATED
            || record.getIntent() == ClusterVariableIntent.UPDATED
            || record.getIntent() == ClusterVariableIntent.DELETED);
  }

  @Override
  public void export(final Record<ClusterVariableRecordValue> record) {
    if (record.getIntent() == ClusterVariableIntent.CREATED) {
      clusterVariableWriter.create(map(record));
    } else if (record.getIntent() == ClusterVariableIntent.UPDATED) {
      clusterVariableWriter.update(map(record));
    } else if (record.getIntent() == ClusterVariableIntent.DELETED) {
      clusterVariableWriter.delete(map(record));
    }
  }

  private ClusterVariableDbModel map(final Record<ClusterVariableRecordValue> record) {
    final var value = record.getValue();
    final var builder =
        new ClusterVariableDbModel.ClusterVariableDbModelBuilder()
            .name(value.getName())
            .value(value.getValue());
    if (value.getScope() == GLOBAL) {
      builder.scope(ClusterVariableScope.GLOBAL).tenantId(null);
    } else {
      builder.scope(ClusterVariableScope.TENANT).tenantId(value.getTenantId());
    }
    return builder.build();
  }
}
