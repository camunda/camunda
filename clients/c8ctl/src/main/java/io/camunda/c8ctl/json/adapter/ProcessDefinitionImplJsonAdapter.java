/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.c8ctl.json.adapter;

import io.avaje.jsonb.JsonAdapter;
import io.avaje.jsonb.JsonReader;
import io.avaje.jsonb.JsonWriter;
import io.avaje.jsonb.Jsonb;
import io.avaje.jsonb.spi.PropertyNames;
import io.avaje.jsonb.spi.ViewBuilder;
import io.avaje.jsonb.spi.ViewBuilderAware;
import io.camunda.client.impl.search.response.ProcessDefinitionImpl;
import java.lang.invoke.MethodHandle;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

public class ProcessDefinitionImplJsonAdapter
    implements JsonAdapter<ProcessDefinitionImpl>, ViewBuilderAware {

  private static final List<FieldMetaData<?>> FIELD_META_DATA = new ArrayList<>();
  private final Jsonb jsonb;
  private final PropertyNames names;
  private final Class<?> clazz = ProcessDefinitionImpl.class;

  public ProcessDefinitionImplJsonAdapter(final Jsonb jsonb) {
    // FIXME extract methods to abstract class
    this.jsonb = jsonb;

    addField("name", String.class, ProcessDefinitionImpl::getName);
    addField("processDefinitionId", String.class, ProcessDefinitionImpl::getProcessDefinitionId);
    addField("version", Integer.class, ProcessDefinitionImpl::getVersion);
    addField("resourceName", String.class, ProcessDefinitionImpl::getResourceName);
    addField("tenantId", String.class, ProcessDefinitionImpl::getTenantId);
    addField("processDefinitionKey", Long.class, ProcessDefinitionImpl::getProcessDefinitionKey);

    // init property names
    final var fieldNames = FIELD_META_DATA.stream().map(FieldMetaData::fieldName).toList();
    names = jsonb.properties(fieldNames.toArray(new String[0]));
  }

  public <T> void addField(
      final String fieldName,
      final Class<T> fieldClass,
      final Function<ProcessDefinitionImpl, T> valueSupplier) {
    FIELD_META_DATA.add(new FieldMetaData<>(fieldName, fieldClass, valueSupplier));
  }

  @Override
  public void build(final ViewBuilder builder, final String name, final MethodHandle handle) {
    builder.beginObject(name, handle);

    for (final FieldMetaData<?> fieldData : FIELD_META_DATA) {
      final var fieldName = fieldData.fieldName;
      final Class<?> fieldClass = fieldData.fieldClass;
      final var adapter = jsonb.adapter(fieldClass);

      final String getterName = buildGetterName(fieldName);
      builder.add(fieldName, adapter, builder.method(clazz, getterName, fieldClass));
    }

    // builder.add(
    //    "name",
    //    pintJsonAdapter,
    //    builder.method(ProcessDefinition.class, "getName", java.lang.String.class));
    // builder.add(
    //    "processDefinitionId",
    //    stringJsonAdapter,
    //    builder.method(ProcessDefinition.class, "getProcessDefinitionId",
    // java.lang.String.class));
    // builder.add(
    //    "version",
    //    pintJsonAdapter,
    //    builder.method(ProcessDefinition.class, "getVersion", int.class));
    // builder.add(
    //    "resourceName",
    //    stringJsonAdapter,
    //    builder.method(ProcessDefinition.class, "getResourceName", java.lang.String.class));
    // builder.add(
    //    "tenantId",
    //    stringJsonAdapter,
    //    builder.method(ProcessDefinition.class, "getTenantId", java.lang.String.class));
    // builder.add(
    //    "processDefinitionKey",
    //    longJsonAdapter,
    //    builder.method(ProcessDefinition.class, "getProcessDefinitionKey", long.class));
    builder.endObject();
  }

  private static String buildGetterName(final String fieldName) {
    return "get" + fieldName.substring(0, 1).toUpperCase() + fieldName.substring(1);
  }

  @Override
  public void toJson(final JsonWriter writer, final ProcessDefinitionImpl processDefinition) {
    writer.beginObject(names);

    for (int i = 0; i < FIELD_META_DATA.size(); i++) {
      final FieldMetaData<?> fieldData = FIELD_META_DATA.get(i);
      final var fieldClass = fieldData.fieldClass;

      writer.name(i);

      final JsonAdapter<Object> adapter = (JsonAdapter<Object>) jsonb.adapter(fieldClass);
      final var value = fieldData.valueSupplier.apply(processDefinition);
      adapter.toJson(writer, value);
    }

    // writer.name(0);
    // stringJsonAdapter.toJson(writer, processDefinition.getName());
    // writer.name(1);
    // stringJsonAdapter.toJson(writer, processDefinition.getProcessDefinitionId());
    // writer.name(2);
    // pintJsonAdapter.toJson(writer, processDefinition.getVersion());
    // writer.name(3);
    // stringJsonAdapter.toJson(writer, processDefinition.getResourceName());
    // writer.name(4);
    // stringJsonAdapter.toJson(writer, processDefinition.getTenantId());
    // writer.name(5);
    // longJsonAdapter.toJson(writer, processDefinition.getProcessDefinitionKey());
    writer.endObject();
  }

  @Override
  public ProcessDefinitionImpl fromJson(final JsonReader reader) {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean isViewBuilderAware() {
    return true;
  }

  @Override
  public ViewBuilderAware viewBuild() {
    return this;
  }

  record FieldMetaData<T>(
      String fieldName, Class<T> fieldClass, Function<ProcessDefinitionImpl, T> valueSupplier) {}
}
