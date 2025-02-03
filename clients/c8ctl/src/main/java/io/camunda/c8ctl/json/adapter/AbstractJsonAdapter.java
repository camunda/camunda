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
import java.lang.invoke.MethodHandle;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

public abstract class AbstractJsonAdapter<R> implements JsonAdapter<R>, ViewBuilderAware {

  private final Jsonb jsonb;
  private final List<FieldMetaData<R, ?>> fieldMetaData = new ArrayList<>();
  private final PropertyNames names;

  protected AbstractJsonAdapter(final Jsonb jsonb) {
    this.jsonb = jsonb;

    addFields();

    // init property names
    final var fieldNames = fieldMetaData.stream().map(FieldMetaData::fieldName).toList();
    names = jsonb.properties(fieldNames.toArray(new String[0]));
  }

  protected abstract void addFields();

  protected abstract Class<R> getResourceClass();

  public <T> void addField(
      final String fieldName, final Class<T> fieldClass, final Function<R, T> valueSupplier) {
    fieldMetaData.add(new FieldMetaData<>(fieldName, fieldClass, valueSupplier));
  }

  // FIXME `ViewBuilderAware.build` is not really needed
  @Override
  public void build(final ViewBuilder builder, final String name, final MethodHandle handle) {
    builder.beginObject(name, handle);

    for (final FieldMetaData<R, ?> fieldData : fieldMetaData) {
      final var fieldName = fieldData.fieldName;
      final Class<?> fieldClass = fieldData.fieldClass;
      final var adapter = jsonb.adapter(fieldClass);

      final String getterName = buildGetterName(fieldName);
      builder.add(fieldName, adapter, builder.method(getResourceClass(), getterName, fieldClass));
    }
    builder.endObject();
  }

  private static String buildGetterName(final String fieldName) {
    return "get" + fieldName.substring(0, 1).toUpperCase() + fieldName.substring(1);
  }

  @Override
  public void toJson(final JsonWriter writer, final R resource) {
    writer.beginObject(names);

    for (int i = 0; i < fieldMetaData.size(); i++) {
      final FieldMetaData<R, ?> fieldData = fieldMetaData.get(i);
      final var fieldClass = fieldData.fieldClass;

      writer.name(i);

      final JsonAdapter<Object> adapter = (JsonAdapter<Object>) jsonb.adapter(fieldClass);
      final var value = fieldData.valueSupplier.apply(resource);
      adapter.toJson(writer, value);
    }
    writer.endObject();
  }

  @Override
  public R fromJson(final JsonReader reader) {
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

  record FieldMetaData<R, T>(String fieldName, Class<T> fieldClass, Function<R, T> valueSupplier) {}
}
