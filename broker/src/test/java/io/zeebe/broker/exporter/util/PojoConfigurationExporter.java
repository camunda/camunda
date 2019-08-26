/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.broker.exporter.util;

import io.zeebe.exporter.api.Exporter;
import io.zeebe.exporter.api.context.Context;
import io.zeebe.protocol.record.Record;

public class PojoConfigurationExporter implements Exporter {

  public static PojoExporterConfiguration configuration;

  @Override
  public void configure(Context context) {
    configuration = context.getConfiguration().instantiate(PojoExporterConfiguration.class);
  }

  @Override
  public void export(Record record) {}

  public PojoExporterConfiguration getConfiguration() {
    return configuration;
  }

  public class PojoExporterConfiguration {

    private String foo = "";
    private int x;
    private PojoExporterConfigurationPart nested;

    public String getFoo() {
      return foo;
    }

    public void setFoo(String foo) {
      this.foo = foo;
    }

    public int getX() {
      return x;
    }

    public void setX(int x) {
      this.x = x;
    }

    public PojoExporterConfigurationPart getNested() {
      return nested;
    }

    public void setNested(PojoExporterConfigurationPart nested) {
      this.nested = nested;
    }
  }

  public class PojoExporterConfigurationPart {
    private String bar;
    private double y;

    public String getBar() {
      return bar;
    }

    public void setBar(String bar) {
      this.bar = bar;
    }

    public double getY() {
      return y;
    }

    public void setY(double y) {
      this.y = y;
    }
  }
}
