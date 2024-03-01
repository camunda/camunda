/*
 * Copyright Camunda Services GmbH
 *
 * BY INSTALLING, DOWNLOADING, ACCESSING, USING, OR DISTRIBUTING THE SOFTWARE, YOU INDICATE YOUR ACCEPTANCE TO AND ARE ENTERING INTO A CONTRACT WITH, THE LICENSOR ON THE TERMS SET OUT IN THIS AGREEMENT. IF YOU DO NOT AGREE TO THESE TERMS, YOU MUST NOT USE THE SOFTWARE. IF YOU ARE RECEIVING THE SOFTWARE ON BEHALF OF A LEGAL ENTITY, YOU REPRESENT AND WARRANT THAT YOU HAVE THE ACTUAL AUTHORITY TO AGREE TO THE TERMS AND CONDITIONS OF THIS AGREEMENT ON BEHALF OF SUCH ENTITY.
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
package io.camunda.operate.util.j5templates;

import static io.camunda.operate.util.ThreadUtil.sleepFor;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.operate.property.OperateProperties;
import io.camunda.operate.schema.SchemaManager;
import io.camunda.operate.util.TestUtil;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class SearchContainerManager {
  protected static final Logger logger = LoggerFactory.getLogger(SearchContainerManager.class);

  protected final OperateProperties operateProperties;
  protected final SchemaManager schemaManager;
  protected String indexPrefix;

  public SearchContainerManager(OperateProperties operateProperties, SchemaManager schemaManager) {
    this.operateProperties = operateProperties;
    this.schemaManager = schemaManager;
  }

  public void refreshIndices(String indexPattern) {
    schemaManager.refresh(indexPattern);
  }

  public void startContainer() {
    if (indexPrefix == null) {
      indexPrefix = TestUtil.createRandomString(10) + "-operate";
    }
    updatePropertiesIndexPrefix();
    if (shouldCreateSchema()) {
      schemaManager.createSchema();
      assertThat(areIndicesCreatedAfterChecks(indexPrefix, 5, 5 * 60 /*sec*/))
          .describedAs("Search %s (min %d) indices are created", indexPrefix, 5)
          .isTrue();
    }
  }

  protected abstract void updatePropertiesIndexPrefix();

  protected abstract boolean shouldCreateSchema();

  protected boolean areIndicesCreatedAfterChecks(
      String indexPrefix, int minCountOfIndices, int maxChecks) {
    boolean areCreated = false;
    int checks = 0;
    while (!areCreated && checks <= maxChecks) {
      checks++;
      try {
        areCreated = areIndicesCreated(indexPrefix, minCountOfIndices);
      } catch (Exception t) {
        logger.error(
            "Search indices (min {}) are not created yet. Waiting {}/{}",
            minCountOfIndices,
            checks,
            maxChecks);
        sleepFor(200);
      }
    }
    logger.debug("Search indices are created after {} checks", checks);
    return areCreated;
  }

  protected abstract boolean areIndicesCreated(String indexPrefix, int minCountOfIndices)
      throws IOException;

  public abstract void stopContainer();
}
