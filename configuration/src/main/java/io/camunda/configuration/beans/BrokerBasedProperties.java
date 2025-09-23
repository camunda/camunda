/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration.beans;

import io.camunda.zeebe.broker.system.configuration.BrokerCfg;
import io.camunda.zeebe.broker.system.configuration.ExporterCfg;
import java.util.LinkedHashMap;
import java.util.List;

// NOTE: This class has been moved away from dist. The reason for this is that as, for now,
//  we're storing the unified configuration objects and beans in the configuration module,
//  the fact that dist depends on configuration doesn't allow us to declare that configuration
//  depends on dist, to extend and override these classes. Nevertheless, depending on dist is
//  a conceptual misrepresentation of what the configuration module belongs to.
//
//  As we are planning, as a future refactoring, to move all the configuration classes and beans
//  within dist (reason: configuration should be part of the Presentation layer of the app), when
//  such refactoring happens, we can bring back GatewayBasedProperties within dist, as there will
//  be no more circular dependency issues.

public class BrokerBasedProperties extends BrokerCfg {

  public ExporterCfg getCamundaExporter() {
    return getExporters().get("camundaexporter");
  }

  public ExporterCfg getRdbmsExporter() {
    return getExporters().get("rdbms");
  }

  public ExporterCfg getExporterConfig(final String exporterClassName, final String exporterName) {
    final List<ExporterCfg> exporters =
        getExporters().values().stream()
            .filter(e -> e.getClassName().equals(exporterClassName))
            .toList();

    final ExporterCfg exporter;
    if (exporters.isEmpty()) {
      exporter = new ExporterCfg();
      exporter.setClassName(exporterClassName);
      exporter.setArgs(new LinkedHashMap<>());
      getExporters().put(exporterName, exporter);
    } else {
      exporter = exporters.getFirst();
    }

    return exporter;
  }
}
