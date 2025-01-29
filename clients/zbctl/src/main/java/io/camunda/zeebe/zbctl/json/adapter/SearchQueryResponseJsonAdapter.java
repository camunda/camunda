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
import io.camunda.client.api.search.response.SearchQueryResponse;
import io.camunda.client.api.search.response.SearchResponsePage;
import java.lang.invoke.MethodHandle;
import java.lang.reflect.Type;
import java.util.List;

public class SearchQueryResponseJsonAdapter<T>
    implements JsonAdapter<SearchQueryResponse<T>>, ViewBuilderAware {

  // public static final JsonAdapter.Factory FACTORY =
  //    (type, jsonb) -> {
  //      if (Types.isGenericTypeOf(type, SearchQueryResponse.class)) {
  //        final Type[] args = Types.typeArguments(type);
  //        return new SearchQueryResponseJsonAdapter<>(jsonb, args[0]);
  //      }
  //      return null;
  //    };

  // naming convention Match

  private final JsonAdapter<List<T>> listTJsonAdapter;
  private final JsonAdapter<SearchResponsePage> searchResponsePageJsonAdapter;
  private final PropertyNames names;

  public SearchQueryResponseJsonAdapter(final Jsonb jsonb, final Type param0) {
    listTJsonAdapter = jsonb.adapter(param0);
    searchResponsePageJsonAdapter = jsonb.adapter(SearchResponsePage.class);
    names = jsonb.properties("items", "page");
  }

  /** Construct using Object for generic type parameters. */
  public SearchQueryResponseJsonAdapter(final Jsonb jsonb) {
    this(jsonb, Object.class);
  }

  @Override
  public void build(final ViewBuilder builder, final String name, final MethodHandle handle) {
    builder.beginObject(name, handle);
    builder.add(
        "items",
        listTJsonAdapter,
        builder.method(SearchQueryResponse.class, "items", java.util.List.class));
    builder.add(
        "page",
        searchResponsePageJsonAdapter,
        builder.method(
            SearchQueryResponse.class,
            "page",
            io.camunda.client.api.search.response.SearchResponsePage.class));
    builder.endObject();
  }

  @Override
  public void toJson(final JsonWriter writer, final SearchQueryResponse searchQueryResponse) {
    writer.beginObject(names);
    writer.name(0);
    listTJsonAdapter.toJson(writer, searchQueryResponse.items());
    writer.name(1);
    searchResponsePageJsonAdapter.toJson(writer, searchQueryResponse.page());
    writer.endObject();
  }

  @Override
  public SearchQueryResponse<T> fromJson(final JsonReader reader) {
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
}
