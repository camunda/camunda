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
package io.camunda.tasklist.webapp.api.rest.v1.controllers.internal;

import io.camunda.tasklist.data.DataGenerator;
import io.camunda.tasklist.es.RetryElasticsearchClient;
import io.camunda.tasklist.property.TasklistProperties;
import io.camunda.tasklist.schema.indices.ProcessIndex;
import io.camunda.tasklist.schema.manager.SchemaManager;
import io.camunda.tasklist.webapp.es.cache.ProcessCache;
import io.camunda.tasklist.webapp.security.TasklistURIs;
import io.camunda.tasklist.webapp.security.se.SearchEngineUserDetailsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.io.IOException;
import java.util.Set;
import java.util.stream.Collectors;
import org.elasticsearch.action.admin.indices.delete.DeleteIndexRequest;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Profile("e2e-test")
@Tag(name = "Util", description = "This API it's just for internal use as dev utilities.")
@RestController
@RequestMapping(value = TasklistURIs.DEV_UTIL_URL_V1, produces = MediaType.APPLICATION_JSON_VALUE)
public class DevUtilExternalController {

  @Autowired private SchemaManager schemaManager;

  @Autowired private RestHighLevelClient esClient;

  @Autowired private DataGenerator devDataGenerator;

  @Autowired private SearchEngineUserDetailsService searchEngineUserDetailsService;

  @Autowired private RetryElasticsearchClient retryElasticsearchClient;

  @Autowired private ProcessCache processCache;

  @Autowired private ProcessIndex processIndex;

  @Autowired private TasklistProperties tasklistProperties;

  @Operation(
      summary = "Get details about the current user.",
      responses = {
        @ApiResponse(
            description = "On success returned",
            responseCode = "200",
            useReturnTypeSchema = true)
      })
  @PostMapping("recreateData")
  public ResponseEntity<?> recreateData() throws IOException {
    final DeleteIndexRequest deleteRequest = new DeleteIndexRequest();

    final Set<String> indices =
        retryElasticsearchClient
            .getIndexNames(tasklistProperties.getElasticsearch().getIndexPrefix() + "*")
            .stream()
            .filter(f -> !f.equals(processIndex.getFullQualifiedName()))
            .collect(Collectors.toSet());

    System.out.println("indexes: ");
    for (final String i : indices) {
      System.out.println(i);
    }

    deleteRequest.indices(indices.toArray(new String[indices.size()]));
    esClient.indices().delete(deleteRequest, RequestOptions.DEFAULT);
    processCache.clearCache();
    schemaManager.createSchema();
    searchEngineUserDetailsService.initializeUsers();
    devDataGenerator.createDemoUsers();
    return ResponseEntity.ok().build();
  }
}
