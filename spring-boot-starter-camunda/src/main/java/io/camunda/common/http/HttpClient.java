package io.camunda.common.http;

import com.google.common.reflect.TypeToken;
import io.camunda.common.auth.Product;
import java.util.Map;

/** Interface to enable swappable http client implementations */
public interface HttpClient {

  void init(String host, String basePath);

  void loadMap(Product product, Map<Class<?>, String> map);

  <T> T get(Class<T> responseType, Long key);

  <T> T get(Class<T> responseType, String id);

  <T, V, W> T get(Class<T> responseType, Class<V> parameterType, TypeToken<W> selector, Long key);

  <T> String getXml(Class<T> selector, Long key);

  <T, V, W, U> T post(Class<T> responseType, Class<V> parameterType, TypeToken<W> selector, U body);

  <T, V> T delete(Class<T> responseType, Class<V> selector, Long key);
}
