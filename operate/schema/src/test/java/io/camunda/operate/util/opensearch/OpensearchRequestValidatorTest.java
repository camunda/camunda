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
package io.camunda.operate.util.opensearch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.operate.exceptions.PersistenceException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.opensearch.client.json.JsonpMapper;
import org.opensearch.client.json.jackson.JacksonJsonpMapper;
import org.opensearch.client.opensearch._types.Refresh;
import org.opensearch.client.opensearch._types.Time;
import org.opensearch.client.opensearch._types.WaitForActiveShards;
import org.opensearch.client.opensearch.core.BulkRequest;
import org.opensearch.client.opensearch.core.bulk.BulkOperation;
import org.opensearch.client.opensearch.core.bulk.IndexOperation;
import org.opensearch.client.opensearch.core.search.SourceConfigParam;

public class OpensearchRequestValidatorTest {

  private final JsonpMapper jsonpMapper = new JacksonJsonpMapper(new ObjectMapper());

  @Test
  public void shouldReturnAllValidRequests() throws PersistenceException {

    // given
    final List<BulkOperation> operations =
        List.of(createIndexOperation("id1", "index1"), createIndexOperation("id2", "index2"));
    final BulkRequest.Builder builder = new BulkRequest.Builder();
    final BulkRequest bulkRequest = builder.operations(operations).build();

    // when
    final BulkRequest validatedBulkRequest =
        OpensearchRequestValidator.validateIndices(bulkRequest, false, jsonpMapper);

    // then
    assertThat(validatedBulkRequest.operations().size()).isEqualTo(2);
  }

  @Test
  public void shouldRemoveInvalidRequestsWhenIgnore() throws PersistenceException {

    // given
    final List<BulkOperation> operations =
        List.of(createIndexOperation("id1", "index1"), createIndexOperation("id2", null));
    final BulkRequest.Builder builder = new BulkRequest.Builder();
    final BulkRequest bulkRequest =
        builder
            .index("indexA")
            .operations(operations)
            .pipeline("pipelineA")
            .refresh(Refresh.True)
            .routing("routingA")
            .requireAlias(true)
            .source(new SourceConfigParam.Builder().fetch(false).build())
            .sourceExcludes(List.of("excludeA"))
            .sourceIncludes(List.of("includeA"))
            .timeout(new Time.Builder().offset(5).build())
            .waitForActiveShards(new WaitForActiveShards.Builder().count(1).build())
            .build();

    // when
    final BulkRequest validatedBulkRequest =
        OpensearchRequestValidator.validateIndices(bulkRequest, true, jsonpMapper);

    // then
    assertThat(validatedBulkRequest.operations().size()).isEqualTo(1);
    assertThat(validatedBulkRequest.operations().get(0).index().id()).isEqualTo("id1");
    assertThat(validatedBulkRequest.pipeline()).isEqualTo("pipelineA");
    assertThat(validatedBulkRequest.refresh()).isEqualTo(Refresh.True);
    assertThat(validatedBulkRequest.routing()).isEqualTo("routingA");
    assertThat(validatedBulkRequest.requireAlias()).isEqualTo(true);
    assertThat(validatedBulkRequest.source()).isNotNull();
    assertThat(validatedBulkRequest.source().fetch()).isEqualTo(false);
    assertThat(validatedBulkRequest.sourceExcludes().get(0)).isEqualTo("excludeA");
    assertThat(validatedBulkRequest.sourceIncludes().get(0)).isEqualTo("includeA");
    assertThat(validatedBulkRequest.timeout()).isNotNull();
    assertThat(validatedBulkRequest.timeout().offset()).isEqualTo(5);
    assertThat(validatedBulkRequest.waitForActiveShards()).isNotNull();
    assertThat(validatedBulkRequest.waitForActiveShards().count()).isEqualTo(1);
  }

  @Test
  public void shouldThrowWhenInvalidRequestsAndNotIgnore() throws PersistenceException {

    // given
    final List<BulkOperation> operations =
        List.of(createIndexOperation("id1", "index1"), createIndexOperation("id2", null));
    final BulkRequest.Builder builder = new BulkRequest.Builder();
    final BulkRequest bulkRequest = builder.operations(operations).build();

    // when
    final PersistenceException exception =
        assertThrows(
            PersistenceException.class,
            () -> OpensearchRequestValidator.validateIndices(bulkRequest, false, jsonpMapper));

    // then
    assertThat(exception.getMessage())
        .startsWith("Bulk request has 1 requests with missing index:");
  }

  private BulkOperation createIndexOperation(String id, String index) {
    return IndexOperation.of(
            o -> {
              o.id(id).index(index).document(new Object());
              return o;
            })
        ._toBulkOperation();
  }
}
