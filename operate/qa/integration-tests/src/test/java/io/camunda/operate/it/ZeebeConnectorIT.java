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
package io.camunda.operate.it;

import io.camunda.operate.property.OperateProperties;
import io.camunda.operate.rest.HealthCheckIT.AddManagementPropertiesInitializer;
import io.camunda.operate.util.*;
import io.camunda.operate.zeebe.PartitionHolder;
import io.camunda.operate.zeebeimport.ZeebeImporter;
import java.util.List;
import org.assertj.core.api.Assertions;
import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.util.ReflectionTestUtils;

@SpringBootTest(
    classes = {TestApplication.class},
    properties = {
      OperateProperties.PREFIX + ".importer.startLoadingDataOnStartup = false",
      OperateProperties.PREFIX + ".archiver.rolloverEnabled = false",
      OperateProperties.PREFIX + ".zeebe.gatewayAddress = localhost:55500",
      "spring.mvc.pathmatch.matching-strategy=ANT_PATH_MATCHER"
    })
@ContextConfiguration(initializers = AddManagementPropertiesInitializer.class)
public class ZeebeConnectorIT extends OperateAbstractIT {

  @Rule public SearchTestRule searchTestRule = new SearchTestRule();

  @Autowired private ZeebeImporter zeebeImporter;

  @Autowired private PartitionHolder partitionHolder;

  @Autowired private OperateProperties operateProperties;

  @Autowired private OperateZeebeRuleProvider operateZeebeRuleProvider;

  @After
  public void cleanup() {
    operateZeebeRuleProvider.finished(null);
  }

  @Test
  public void testZeebeConnection() throws Exception {
    // when 1
    // no Zeebe broker is running

    // then 1
    // application context must be successfully started
    getRequest("/actuator/health/liveness");
    // import is working fine
    zeebeImporter.performOneRoundOfImport();
    // partition list is empty
    Assertions.assertThat(getPartitionIds()).isEmpty();

    // when 2
    // Zeebe is started
    startZeebe();

    // then 2
    // data import is working
    zeebeImporter.performOneRoundOfImport();
    // partition list is not empty
    Assertions.assertThat(getPartitionIds()).isNotEmpty();
  }

  private List<Integer> getPartitionIds() {
    return (List<Integer>) ReflectionTestUtils.getField(partitionHolder, "partitionIds");
  }

  private void startZeebe() {
    operateZeebeRuleProvider.starting(null);
    operateProperties.getZeebeElasticsearch().setPrefix(operateZeebeRuleProvider.getPrefix());

    partitionHolder.setZeebeClient(operateZeebeRuleProvider.getClient());
  }

  @Test
  public void testRecoverAfterZeebeRestart() throws Exception {
    // when 1
    // Zeebe is started
    startZeebe();

    // then 1
    // data import is working
    zeebeImporter.performOneRoundOfImport();

    // when 2
    // Zeebe is restarted
    operateZeebeRuleProvider.finished(null);
    operateZeebeRuleProvider.starting(null);

    // then 2
    // data import is still working
    zeebeImporter.performOneRoundOfImport();
  }
}
