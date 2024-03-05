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
package io.camunda.operate.util;

import static io.camunda.operate.qa.util.ContainerVersionsUtil.ZEEBE_CURRENTVERSION_PROPERTY_NAME;
import static io.camunda.operate.store.opensearch.dsl.RequestDSL.componentTemplateRequestBuilder;
import static org.junit.Assert.assertTrue;

import io.camunda.operate.conditions.OpensearchCondition;
import io.camunda.operate.property.OperateProperties;
import io.camunda.operate.qa.util.ContainerVersionsUtil;
import io.camunda.operate.qa.util.TestContainerUtil;
import io.camunda.operate.store.opensearch.client.sync.ZeebeRichOpenSearchClient;
import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.client.api.command.ClientException;
import io.camunda.zeebe.client.api.response.Topology;
import io.zeebe.containers.ZeebeContainer;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import org.junit.runner.Description;
import org.opensearch.client.opensearch.cluster.ComponentTemplateSummary;
import org.opensearch.client.opensearch.indices.IndexSettings;
import org.opensearch.client.opensearch.indices.IndexState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Conditional(OpensearchCondition.class)
@Component
public class OpensearchOperateZeebeRuleProvider implements OperateZeebeRuleProvider {

  public static final String YYYY_MM_DD = "uuuu-MM-dd";
  private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(15);
  private static final Logger logger =
      LoggerFactory.getLogger(OpensearchOperateZeebeRuleProvider.class);
  @Autowired public OperateProperties operateProperties;

  @Autowired protected ZeebeRichOpenSearchClient zeebeRichOpenSearchClient;
  protected ZeebeContainer zeebeContainer;
  @Autowired private TestContainerUtil testContainerUtil;
  private ZeebeClient client;

  private String prefix;
  private boolean failed = false;

  @Override
  public void starting(Description description) {
    this.prefix = TestUtil.createRandomString(10);
    logger.info("Starting Zeebe with OS prefix: " + prefix);
    operateProperties.getZeebeOpensearch().setPrefix(prefix);

    startZeebe();
  }

  public void updateRefreshInterval(String value) {
    ComponentTemplateSummary template =
        zeebeRichOpenSearchClient.template().getComponentTemplate().get(prefix).template();
    IndexSettings indexSettings = template.settings().get("index");
    IndexSettings newSettings =
        IndexSettings.of(b -> b.index(indexSettings).refreshInterval(ri -> ri.time(value)));
    IndexState newTemplate =
        IndexState.of(t -> t.settings(newSettings).mappings(template.mappings()));
    var requestBuilder = componentTemplateRequestBuilder(prefix).template(newTemplate);
    assertTrue(
        zeebeRichOpenSearchClient
            .template()
            .createComponentTemplateWithRetries(requestBuilder.build()));
  }

  public void refreshIndices(Instant instant) {
    String date =
        DateTimeFormatter.ofPattern(YYYY_MM_DD).withZone(ZoneId.systemDefault()).format(instant);
    zeebeRichOpenSearchClient.index().refresh(prefix + "*" + date);
  }

  @Override
  public void finished(Description description) {
    stopZeebe();
    if (client != null) {
      client.close();
      client = null;
    }
    if (!failed) {
      TestUtil.removeAllIndices(
          zeebeRichOpenSearchClient.index(), zeebeRichOpenSearchClient.template(), prefix);
    }
  }

  @Override
  public void failed(Throwable e, Description description) {
    this.failed = true;
  }

  /**
   * Starts the broker and the client. This is blocking and will return once the broker is ready to
   * accept commands.
   *
   * @throws IllegalStateException if no exporter has previously been configured
   */
  public void startZeebe() {

    final String zeebeVersion =
        ContainerVersionsUtil.readProperty(ZEEBE_CURRENTVERSION_PROPERTY_NAME);
    zeebeContainer =
        testContainerUtil.startZeebe(zeebeVersion, prefix, 2, isMultitTenancyEnabled());

    client =
        ZeebeClient.newClientBuilder()
            .gatewayAddress(zeebeContainer.getExternalGatewayAddress())
            .usePlaintext()
            .defaultRequestTimeout(REQUEST_TIMEOUT)
            .build();

    testZeebeIsReady();
  }

  /** Stops the broker and destroys the client. Does nothing if not started yet. */
  public void stopZeebe() {
    testContainerUtil.stopZeebe(null);
  }

  public String getPrefix() {
    return prefix;
  }

  public void setPrefix(String prefix) {
    this.prefix = prefix;
  }

  public ZeebeContainer getZeebeContainer() {
    return zeebeContainer;
  }

  public ZeebeClient getClient() {
    return client;
  }

  @Override
  public boolean isMultitTenancyEnabled() {
    return operateProperties.getMultiTenancy().isEnabled();
  }

  private void testZeebeIsReady() {
    // get topology to check that cluster is available and ready for work
    Topology topology = null;
    while (topology == null) {
      try {
        topology = client.newTopologyRequest().send().join();
      } catch (ClientException ex) {
        ex.printStackTrace();
      }
    }
  }
}
