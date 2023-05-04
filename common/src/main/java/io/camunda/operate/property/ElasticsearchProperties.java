/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.property;

import static io.camunda.operate.util.ConversionUtils.stringIsEmpty;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.function.Function;

public class ElasticsearchProperties {

  public static final String DATE_FORMAT_DEFAULT = "yyyy-MM-dd'T'HH:mm:ss.SSSZZ";

  public static final String ELS_DATE_FORMAT_DEFAULT = "date_time";

  public static final int BULK_REQUEST_MAX_SIZE_IN_BYTES_DEFAULT = 1024 * 1024 * 90; // 90 MB

  private String clusterName= "elasticsearch";

  @Deprecated
  private String host = "localhost";

  @Deprecated
  private int port = 9200;

  private String dateFormat = DATE_FORMAT_DEFAULT;

  private String elsDateFormat = ELS_DATE_FORMAT_DEFAULT;

  private int batchSize = 200;

  private Integer socketTimeout;
  private Integer connectTimeout;

  private boolean createSchema = true;

  private String url;
  private String username;
  private String password;

  private int bulkRequestMaxSizeInBytes = BULK_REQUEST_MAX_SIZE_IN_BYTES_DEFAULT;

  @NestedConfigurationProperty
  private SslProperties ssl;;

  public String getClusterName() {
    return clusterName;
  }

  public void setClusterName(String clusterName) {
    this.clusterName = clusterName;
  }

  @Deprecated
  public String getHost() {
    return getFromURIorDefault(URI::getHost, host);
  }

  @Deprecated
  public void setHost(String host) {
    this.host = host;
  }

  @Deprecated
  public int getPort() {
    return getFromURIorDefault(URI::getPort, port);
  }

  private <T> T getFromURIorDefault(Function<URI, T> valueFromURI, T defaultValue) {
    if (!stringIsEmpty(url)) {
      try {
        return valueFromURI.apply(new URI(url));
      } catch (URISyntaxException e) {
        return defaultValue;
      }
    }
    return defaultValue;
  }

  @Deprecated
  public void setPort(int port) {
    this.port = port;
  }

  public String getDateFormat() {
    return dateFormat;
  }

  public void setDateFormat(String dateFormat) {
    this.dateFormat = dateFormat;
  }

  public String getElsDateFormat() {
    return elsDateFormat;
  }

  public void setElsDateFormat(String elsDateFormat) {
    this.elsDateFormat = elsDateFormat;
  }

  public int getBatchSize() {
    return batchSize;
  }

  public void setBatchSize(int batchSize) {
    this.batchSize = batchSize;
  }

  public boolean isCreateSchema() {
    return createSchema;
  }

  public void setCreateSchema(boolean createSchema) {
    this.createSchema = createSchema;
  }

  public String getPassword() {
    return password;
  }

  public void setPassword(String password) {
    this.password = password;
  }

  public String getUsername() {
    return username;
  }

  public void setUsername(String username) {
    this.username = username;
  }

  public String getUrl() {
    if (stringIsEmpty(url)) {
      return String.format("http://%s:%d", getHost(), getPort());
    }
    return url;
  }

  public void setUrl(String url) {
    this.url = url;
  }

  public Integer getSocketTimeout() {
    return socketTimeout;
  }

  public void setSocketTimeout(Integer socketTimeout) {
    this.socketTimeout = socketTimeout;
  }

  public Integer getConnectTimeout() {
    return connectTimeout;
  }

  public void setConnectTimeout(Integer connectTimeout) {
    this.connectTimeout = connectTimeout;
  }

  public SslProperties getSsl() { return ssl; }

  public void setSsl(SslProperties ssl) {
    this.ssl = ssl;
  }

  public long getBulkRequestMaxSizeInBytes() {
    return bulkRequestMaxSizeInBytes;
  }

  public void setBulkRequestMaxSizeInBytes(int bulkRequestMaxSizeInBytes) {
    this.bulkRequestMaxSizeInBytes = bulkRequestMaxSizeInBytes;
  }
}
