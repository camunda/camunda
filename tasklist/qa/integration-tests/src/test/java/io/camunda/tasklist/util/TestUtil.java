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
import java.util.List;
import java.util.UUID;
import org.elasticsearch.ElasticsearchStatusException;
import org.elasticsearch.action.admin.indices.delete.DeleteIndexRequest;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.client.indices.DeleteComposableIndexTemplateRequest;
import org.elasticsearch.client.indices.GetComposableIndexTemplateRequest;
import org.elasticsearch.client.indices.GetIndexRequest;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class TestUtil {

  public static final String DATE_TIME_GRAPHQL_PATTERN = "yyyy-MM-dd'T'HH:mm:ss.SSSxxxx";
  private static final Logger LOGGER = LoggerFactory.getLogger(TestUtil.class);

  public static String createRandomString(int length) {
    return UUID.randomUUID().toString().substring(0, length);
  }

  public static void removeAllIndices(OpenSearchClient osClient, String prefix) {
    try {
      LOGGER.info("Removing indices");
      final var indexResponses = osClient.indices().get(ir -> ir.index(List.of(prefix + "*")));
      final List listIndexResponses = indexResponses.result().keySet().stream().toList();
      if (listIndexResponses.size() > 0) {
        osClient.indices().delete(d -> d.index(listIndexResponses));
      }

      final var templateResponses =
          osClient.indices().getIndexTemplate(it -> it.name(prefix + "*"));

      templateResponses.indexTemplates().stream()
          .forEach(
              t -> {
                try {
                  osClient.indices().deleteIndexTemplate(dit -> dit.name(t.name()));
                } catch (IOException e) {
                  throw new RuntimeException(e);
                }
              });

    } catch (IOException ex) {
      LOGGER.error(ex.getMessage(), ex);
    }
  }

  public static void removeAllIndices(RestHighLevelClient esClient, String prefix) {
    try {
      LOGGER.info("Removing indices");
      final var indexResponses =
          esClient.indices().get(new GetIndexRequest(prefix + "*"), RequestOptions.DEFAULT);
      for (String index : indexResponses.getIndices()) {
        esClient.indices().delete(new DeleteIndexRequest(index), RequestOptions.DEFAULT);
      }
      final var templateResponses =
          esClient
              .indices()
              .getIndexTemplate(
                  new GetComposableIndexTemplateRequest(prefix + "*"), RequestOptions.DEFAULT);
      for (String template : templateResponses.getIndexTemplates().keySet()) {
        esClient
            .indices()
            .deleteIndexTemplate(
                new DeleteComposableIndexTemplateRequest(template), RequestOptions.DEFAULT);
      }
    } catch (ElasticsearchStatusException | IOException ex) {
      LOGGER.error(ex.getMessage(), ex);
    }
  }

  public static boolean isElasticSearch() {
    return !TasklistPropertiesUtil.isOpenSearchDatabase();
  }

  public static boolean isOpenSearch() {
    return TasklistPropertiesUtil.isOpenSearchDatabase();
  }
}
