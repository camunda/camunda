package io.camunda.common.auth;

import java.util.HashMap;
import java.util.Map;

/** Contains mapping between products and their JWT credentials */
public class JwtConfig {

  private Map<Product, JwtCredential> map;

  public JwtConfig() {
    map = new HashMap<>();
  }

  public void addProduct(Product product, JwtCredential jwtCredential) {
    map.put(product, jwtCredential);
  }

  public JwtCredential getProduct(Product product) {
    return map.get(product);
  }

  public Map<Product, JwtCredential> getMap() {
    return map;
  }
}
