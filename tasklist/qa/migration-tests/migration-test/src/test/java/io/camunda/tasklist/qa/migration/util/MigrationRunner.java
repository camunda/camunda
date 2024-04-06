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
package io.camunda.tasklist.qa.migration.util;

import io.camunda.tasklist.qa.util.TestContext;
import io.camunda.tasklist.qa.util.migration.TestFixture;
import io.camunda.tasklist.schema.migration.SchemaMigration;
import jakarta.annotation.PostConstruct;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * This bean performs the main test logic, before assertions can be applied. * Finds in application
 * context list of test fixtures * Select those that are configured to be included in upgrade path
 * (test.properties file) * Apply test fixtures one by one * Migrate data till "version.current"
 */
@Component
public class MigrationRunner {

  private static final Logger LOGGER = LoggerFactory.getLogger(MigrationRunner.class);

  @Autowired private TestContext testContext;

  @Autowired private List<TestFixture> testFixtures;

  private Map<String, TestFixture> testFixtureMap;

  @Value("${upgrade.path}")
  private String[] upgradePath;

  @PostConstruct
  private void init() {
    initTestFixtureMap();
    selectAndRunTestFixtures();
    runMigration();
  }

  private void initTestFixtureMap() {
    testFixtureMap = new HashMap<>();
    for (TestFixture testFixture : testFixtures) {
      testFixtureMap.put(testFixture.getVersion(), testFixture);
    }
  }

  private void selectAndRunTestFixtures() {
    LOGGER.info("Upgrade path under test: {}", upgradePath);
    for (String version : upgradePath) {
      final TestFixture testFixture = testFixtureMap.get(version);
      if (testFixture == null) {
        throw new RuntimeException("No test fixture found for version " + version);
      }
      LOGGER.info("************ Applying test fixture for v. {} ************", version);
      testFixture.setup(testContext);
    }
  }

  private void runMigration() {
    LOGGER.info("************ Migrating data to current version ************");
    try {
      final String[] args = new String[2];
      args[0] =
          "--camunda.tasklist.elasticsearch.url="
              + String.format(
                  "http://%s:%s",
                  testContext.getExternalElsHost(), testContext.getExternalElsPort());
      args[1] =
          "--camunda.tasklist.zeebeelasticsearch.url="
              + String.format(
                  "http://%s:%s",
                  testContext.getExternalElsHost(), testContext.getExternalElsPort());
      SchemaMigration.main(args);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }
}
