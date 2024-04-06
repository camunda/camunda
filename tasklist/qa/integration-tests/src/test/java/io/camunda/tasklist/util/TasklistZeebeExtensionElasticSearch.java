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
package io.camunda.tasklist.util;

import java.io.IOException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import org.elasticsearch.action.admin.indices.refresh.RefreshRequest;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
    name = "camunda.tasklist.database",
    havingValue = "elasticsearch",
    matchIfMissing = true)
public class TasklistZeebeExtensionElasticSearch extends TasklistZeebeExtension {

  @Autowired
  @Qualifier("zeebeEsClient")
  protected RestHighLevelClient zeebeEsClient;

  public void refreshIndices(Instant instant) {
    try {
      final String date =
          DateTimeFormatter.ISO_LOCAL_DATE.withZone(ZoneId.systemDefault()).format(instant);
      final RefreshRequest refreshRequest = new RefreshRequest(getPrefix() + "*" + date);
      zeebeEsClient.indices().refresh(refreshRequest, RequestOptions.DEFAULT);
    } catch (IOException ex) {
      throw new RuntimeException(ex);
    }
  }

  @Override
  protected void setZeebeIndexesPrefix(String prefix) {
    tasklistProperties.getZeebeElasticsearch().setPrefix(prefix);
  }

  @Override
  protected String getZeebeExporterIndexPrefixConfigParameterName() {
    return "ZEEBE_BROKER_EXPORTERS_ELASTICSEARCH_ARGS_INDEX_PREFIX";
  }

  @Override
  protected Map<String, String> getDatabaseEnvironmentVariables(String indexPrefix) {
    return Map.of(
        "ZEEBE_BROKER_EXPORTERS_ELASTICSEARCH_ARGS_URL",
        "http://host.testcontainers.internal:9200",
        "ZEEBE_BROKER_EXPORTERS_ELASTICSEARCH_ARGS_BULK_DELAY",
        "1",
        "ZEEBE_BROKER_EXPORTERS_ELASTICSEARCH_ARGS_BULK_SIZE",
        "1",
        "ZEEBE_BROKER_EXPORTERS_ELASTICSEARCH_ARGS_INDEX_PREFIX",
        indexPrefix,
        "ZEEBE_BROKER_EXPORTERS_ELASTICSEARCH_CLASSNAME",
        "io.camunda.zeebe.exporter.ElasticsearchExporter");
  }

  @Override
  public void afterEach(ExtensionContext extensionContext) {
    super.afterEach(extensionContext);
    if (!failed) {
      TestUtil.removeAllIndices(zeebeEsClient, getPrefix());
    }
  }

  @Override
  public void setZeebeOsClient(OpenSearchClient zeebeOsClient) {}

  @Override
  public void setZeebeEsClient(RestHighLevelClient zeebeOsClient) {
    this.zeebeEsClient = zeebeOsClient;
  }

  @Override
  protected int getDatabasePort() {
    return 9200;
  }
}
