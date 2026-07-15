/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration;

import java.util.LinkedHashMap;
import java.util.Map;
import org.jspecify.annotations.NullMarked;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

/**
 * This section allows configuring named secret stores.
 *
 * <p>Canonical unified configuration properties are under {@code camunda.secrets.*}, including:
 *
 * <ul>
 *   <li>{@code camunda.secrets.stores.file.<id>.path}
 * </ul>
 *
 * <p>Secrets configuration is overridable per physical tenant via {@code
 * camunda.physical-tenants.<id>.secrets.*}.
 */
@NullMarked
public class Secrets {

  @NestedConfigurationProperty private Stores stores = new Stores();

  public Stores getStores() {
    return stores;
  }

  public void setStores(final Stores stores) {
    this.stores = stores;
  }

  public static class Stores {

    private Map<String, FileStore> file = new LinkedHashMap<>();

    public Map<String, FileStore> getFile() {
      return file;
    }

    public void setFile(final Map<String, FileStore> file) {
      this.file = file;
    }
  }

  public static class FileStore {

    /**
     * Path to the directory backing this file-based secret store. Defaults to {@code
     * /etc/camunda/secrets}.
     */
    private String path = "/etc/camunda/secrets";

    public String getPath() {
      return path;
    }

    public void setPath(final String path) {
      this.path = path;
    }
  }
}
