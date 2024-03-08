package io.camunda.common.auth;

import java.util.HashMap;
import java.util.Map;

/** Contains mapping between products and their Simple credentials */
public class SimpleConfig {

  private Map<Product, SimpleCredential> map;

  public SimpleConfig() {
    map = new HashMap<>();
  }

  public void addProduct(Product product, SimpleCredential simpleCredential) {
    map.put(product, simpleCredential);
  }

  public Map<Product, SimpleCredential> getMap() {
    return map;
  }

  public SimpleCredential getProduct(Product product) {
    return map.get(product);
  }
}
