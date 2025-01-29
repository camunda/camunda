/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.zbctl.json.adapter;

import io.avaje.jsonb.JsonAdapter;
import io.avaje.jsonb.JsonReader;
import io.avaje.jsonb.JsonWriter;
import io.avaje.jsonb.Jsonb;
import io.avaje.jsonb.spi.PropertyNames;
import io.avaje.jsonb.spi.ViewBuilder;
import io.avaje.jsonb.spi.ViewBuilderAware;
import io.camunda.client.impl.search.response.ProcessInstanceImpl;
import java.lang.invoke.MethodHandle;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

public class ProcessInstanceImplJsonAdapter
    implements JsonAdapter<ProcessInstanceImpl>, ViewBuilderAware {

  private static final List<FieldMetaData<?>> fieldMetaData = new ArrayList<>();
  private final Jsonb jsonb;
  private final PropertyNames names;
  private final Class<?> clazz = ProcessInstanceImpl.class;

  public ProcessInstanceImplJsonAdapter(final Jsonb jsonb) {
    // FIXME extract methods to abstract class
    this.jsonb = jsonb;
    addField("processInstanceKey", Long.class, ProcessInstanceImpl::getProcessInstanceKey);
    addField("processDefinitionId", String.class, ProcessInstanceImpl::getProcessDefinitionId);
    addField("processDefinitionName", String.class, ProcessInstanceImpl::getProcessDefinitionName);
    addField(
        "processDefinitionVersion",
        Integer.class,
        ProcessInstanceImpl::getProcessDefinitionVersion);
    addField(
        "processDefinitionVersionTag",
        String.class,
        ProcessInstanceImpl::getProcessDefinitionVersionTag);
    addField("processDefinitionKey", Long.class, ProcessInstanceImpl::getProcessDefinitionKey);
    addField(
        "parentProcessInstanceKey", Long.class, ProcessInstanceImpl::getParentProcessInstanceKey);
    addField(
        "parentFlowNodeInstanceKey", Long.class, ProcessInstanceImpl::getParentFlowNodeInstanceKey);
    addField("startDate", String.class, ProcessInstanceImpl::getStartDate);
    addField("endDate", String.class, ProcessInstanceImpl::getEndDate);
    addField("state", String.class, ProcessInstanceImpl::getState);
    addField("hasIncident", Boolean.class, ProcessInstanceImpl::getHasIncident);
    addField("tenantId", String.class, ProcessInstanceImpl::getTenantId);

    // init property names
    final var fieldNames = fieldMetaData.stream().map(FieldMetaData::fieldName).toList();
    names = jsonb.properties(fieldNames.toArray(new String[0]));
  }

  public <T> void addField(
      final String fieldName,
      final Class<T> fieldClass,
      final Function<ProcessInstanceImpl, T> valueSupplier) {
    fieldMetaData.add(new FieldMetaData<>(fieldName, fieldClass, valueSupplier));
  }

  @Override
  public void build(final ViewBuilder builder, final String name, final MethodHandle handle) {
    builder.beginObject(name, handle);

    for (final FieldMetaData<?> fieldData : fieldMetaData) {
      final var fieldName = fieldData.fieldName;
      final Class<?> fieldClass = fieldData.fieldClass;
      final var adapter = jsonb.adapter(fieldClass);

      final String getterName = buildGetterName(fieldName);
      builder.add(fieldName, adapter, builder.method(clazz, getterName, fieldClass));
    }

    builder.endObject();
  }

  private static String buildGetterName(final String fieldName) {
    return "get" + fieldName.substring(0, 1).toUpperCase() + fieldName.substring(1);
  }

  @Override
  public void toJson(final JsonWriter writer, final ProcessInstanceImpl processDefinition) {
    writer.beginObject(names);

    for (int i = 0; i < fieldMetaData.size(); i++) {
      final FieldMetaData<?> fieldData = fieldMetaData.get(i);
      final var fieldClass = fieldData.fieldClass;

      writer.name(i);

      final JsonAdapter<Object> adapter = (JsonAdapter<Object>) jsonb.adapter(fieldClass);
      final var value = fieldData.valueSupplier.apply(processDefinition);
      adapter.toJson(writer, value);
    }

    writer.endObject();
  }

  @Override
  public ProcessInstanceImpl fromJson(final JsonReader reader) {
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
      String fieldName, Class<T> fieldClass, Function<ProcessInstanceImpl, T> valueSupplier) {}
}
