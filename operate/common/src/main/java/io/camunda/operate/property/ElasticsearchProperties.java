/*
 * Copyright Camunda Services GmbH
 *
 * BY INSTALLING, DOWNLOADING, ACCESSING, USING, OR DISTRIBUTING THE SOFTWARE (“USE”), YOU INDICATE YOUR ACCEPTANCE TO AND ARE ENTERING INTO A CONTRACT WITH, THE LICENSOR ON THE TERMS SET OUT IN THIS AGREEMENT. IF YOU DO NOT AGREE TO THESE TERMS, YOU MUST NOT USE THE SOFTWARE. IF YOU ARE RECEIVING THE SOFTWARE ON BEHALF OF A LEGAL ENTITY, YOU REPRESENT AND WARRANT THAT YOU HAVE THE ACTUAL AUTHORITY TO AGREE TO THE TERMS AND CONDITIONS OF THIS AGREEMENT ON BEHALF OF SUCH ENTITY.
 * “Licensee” means you, an individual, or the entity on whose behalf you receive the Software.
 *
 * Permission is hereby granted, free of charge, to the Licensee obtaining a copy of this Software and associated documentation files to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject in each case to the following conditions:
 * Condition 1: If the Licensee distributes the Software or any derivative works of the Software, the Licensee must attach this Agreement.
 * Condition 2: Without limiting other conditions in this Agreement, the grant of rights is solely for non-production use as defined below.
 * "Non-production use" means any use of the Software that is not directly related to creating products, services, or systems that generate revenue or other direct or indirect economic benefits.  Examples of permitted non-production use include personal use, educational use, research, and development. Examples of prohibited production use include, without limitation, use for commercial, for-profit, or publicly accessible systems or use for commercial or revenue-generating purposes.
 *
 * If the Licensee is in breach of the Conditions, this Agreement, including the rights granted under it, will automatically terminate with immediate effect.
 *
 * SUBJECT AS SET OUT BELOW, THE SOFTWARE IS PROVIDED “AS IS”, WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE, AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 * NOTHING IN THIS AGREEMENT EXCLUDES OR RESTRICTS A PARTY’S LIABILITY FOR (A) DEATH OR PERSONAL INJURY CAUSED BY THAT PARTY’S NEGLIGENCE, (B) FRAUD, OR (C) ANY OTHER LIABILITY TO THE EXTENT THAT IT CANNOT BE LAWFULLY EXCLUDED OR RESTRICTED.
 */
package io.camunda.operate.property;

import static io.camunda.operate.util.ConversionUtils.stringIsEmpty;

import io.camunda.operate.connect.OperateDateTimeFormatter;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.function.Function;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

public class ElasticsearchProperties {
  public static final String ELS_DATE_FORMAT_DEFAULT = "date_time";

  public static final int BULK_REQUEST_MAX_SIZE_IN_BYTES_DEFAULT = 1024 * 1024 * 90; // 90 MB

  private String clusterName = "elasticsearch";

  @Deprecated private String host = "localhost";

  @Deprecated private int port = 9200;

  private String dateFormat = OperateDateTimeFormatter.DATE_FORMAT_DEFAULT;

  private String elsDateFormat = ELS_DATE_FORMAT_DEFAULT;

  private int batchSize = 200;

  private Integer socketTimeout;
  private Integer connectTimeout;

  private boolean createSchema = true;

  private String url;
  private String username;
  private String password;

  private int bulkRequestMaxSizeInBytes = BULK_REQUEST_MAX_SIZE_IN_BYTES_DEFAULT;
  private boolean bulkRequestIgnoreNullIndex = false;

  @NestedConfigurationProperty private SslProperties ssl;

  public String getClusterName() {
    return clusterName;
  }

  public void setClusterName(final String clusterName) {
    this.clusterName = clusterName;
  }

  @Deprecated
  public String getHost() {
    return getFromURIorDefault(URI::getHost, host);
  }

  @Deprecated
  public void setHost(final String host) {
    this.host = host;
  }

  @Deprecated
  public int getPort() {
    return getFromURIorDefault(URI::getPort, port);
  }

  @Deprecated
  public void setPort(final int port) {
    this.port = port;
  }

  private <T> T getFromURIorDefault(final Function<URI, T> valueFromURI, final T defaultValue) {
    if (!stringIsEmpty(url)) {
      try {
        return valueFromURI.apply(new URI(url));
      } catch (final URISyntaxException e) {
        return defaultValue;
      }
    }
    return defaultValue;
  }

  public String getDateFormat() {
    return dateFormat;
  }

  public void setDateFormat(final String dateFormat) {
    this.dateFormat = dateFormat;
  }

  public String getElsDateFormat() {
    return elsDateFormat;
  }

  public void setElsDateFormat(final String elsDateFormat) {
    this.elsDateFormat = elsDateFormat;
  }

  public int getBatchSize() {
    return batchSize;
  }

  public void setBatchSize(final int batchSize) {
    this.batchSize = batchSize;
  }

  public boolean isCreateSchema() {
    return createSchema;
  }

  public void setCreateSchema(final boolean createSchema) {
    this.createSchema = createSchema;
  }

  public String getPassword() {
    return password;
  }

  public void setPassword(final String password) {
    this.password = password;
  }

  public String getUsername() {
    return username;
  }

  public void setUsername(final String username) {
    this.username = username;
  }

  public String getUrl() {
    if (stringIsEmpty(url)) {
      return String.format("http://%s:%d", getHost(), getPort());
    }
    return url;
  }

  public void setUrl(final String url) {
    this.url = url;
  }

  public Integer getSocketTimeout() {
    return socketTimeout;
  }

  public void setSocketTimeout(final Integer socketTimeout) {
    this.socketTimeout = socketTimeout;
  }

  public Integer getConnectTimeout() {
    return connectTimeout;
  }

  public void setConnectTimeout(final Integer connectTimeout) {
    this.connectTimeout = connectTimeout;
  }

  public SslProperties getSsl() {
    return ssl;
  }

  public void setSsl(final SslProperties ssl) {
    this.ssl = ssl;
  }

  public long getBulkRequestMaxSizeInBytes() {
    return bulkRequestMaxSizeInBytes;
  }

  public void setBulkRequestMaxSizeInBytes(final int bulkRequestMaxSizeInBytes) {
    this.bulkRequestMaxSizeInBytes = bulkRequestMaxSizeInBytes;
  }

  public boolean isBulkRequestIgnoreNullIndex() {
    return bulkRequestIgnoreNullIndex;
  }

  public void setBulkRequestIgnoreNullIndex(boolean bulkRequestIgnoreNullIndex) {
    this.bulkRequestIgnoreNullIndex = bulkRequestIgnoreNullIndex;
  }
}
