package io.camunda.common.json;

import java.util.Map;

public interface JsonMapper {

  <T> T fromJson(final String json, final Class<T> resultType);

  <T, U> T fromJson(final String json, final Class<T> resultType, final Class<U> parameterType);

  Map<String, Object> fromJsonAsMap(final String json);

  Map<String, String> fromJsonAsStringMap(final String json);

  String toJson(final Object value);
}
