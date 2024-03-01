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
package io.camunda.operate.util;

import static io.camunda.operate.qa.util.ContainerVersionsUtil.ZEEBE_CURRENTVERSION_PROPERTY_NAME;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.operate.conditions.ElasticsearchCondition;
import io.camunda.operate.property.OperateProperties;
import io.camunda.operate.qa.util.ContainerVersionsUtil;
import io.camunda.operate.qa.util.TestContainerUtil;
import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.client.api.command.ClientException;
import io.camunda.zeebe.client.api.response.Topology;
import io.zeebe.containers.ZeebeContainer;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import org.elasticsearch.action.admin.indices.refresh.RefreshRequest;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.client.indices.GetComponentTemplatesRequest;
import org.elasticsearch.client.indices.GetComponentTemplatesResponse;
import org.elasticsearch.client.indices.PutComponentTemplateRequest;
import org.elasticsearch.cluster.metadata.ComponentTemplate;
import org.elasticsearch.cluster.metadata.Template;
import org.elasticsearch.common.settings.Settings;
import org.junit.runner.Description;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Conditional(ElasticsearchCondition.class)
@Component
public class ElasticsearchOperateZeebeRuleProvider implements OperateZeebeRuleProvider {

  public static final String YYYY_MM_DD = "uuuu-MM-dd";
  private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(15);
  private static final Logger logger =
      LoggerFactory.getLogger(ElasticsearchOperateZeebeRuleProvider.class);
  @Autowired public OperateProperties operateProperties;

  @Autowired
  @Qualifier("zeebeEsClient")
  protected RestHighLevelClient zeebeEsClient;

  protected ZeebeContainer zeebeContainer;
  @Autowired private TestContainerUtil testContainerUtil;
  private ZeebeClient client;

  private String prefix;
  private boolean failed = false;

  @Override
  public void starting(Description description) {
    this.prefix = TestUtil.createRandomString(10);
    logger.info("Starting Zeebe with ELS prefix: " + prefix);
    operateProperties.getZeebeElasticsearch().setPrefix(prefix);

    startZeebe();
  }

  public void updateRefreshInterval(String value) {
    try {
      GetComponentTemplatesRequest getRequest = new GetComponentTemplatesRequest(prefix);
      GetComponentTemplatesResponse response =
          zeebeEsClient.cluster().getComponentTemplate(getRequest, RequestOptions.DEFAULT);
      ComponentTemplate componentTemplate = response.getComponentTemplates().get(prefix);
      Settings settings = componentTemplate.template().settings();

      PutComponentTemplateRequest request = new PutComponentTemplateRequest().name(prefix);
      Settings newSettings =
          Settings.builder().put(settings).put("index.refresh_interval", value).build();
      Template newTemplate =
          new Template(newSettings, componentTemplate.template().mappings(), null);
      ComponentTemplate newComponentTemplate = new ComponentTemplate(newTemplate, null, null);
      request.componentTemplate(newComponentTemplate);
      assertThat(
              zeebeEsClient
                  .cluster()
                  .putComponentTemplate(request, RequestOptions.DEFAULT)
                  .isAcknowledged())
          .isTrue();
    } catch (IOException ex) {
      throw new RuntimeException(ex);
    }
  }

  public void refreshIndices(Instant instant) {
    try {
      String date =
          DateTimeFormatter.ofPattern(YYYY_MM_DD).withZone(ZoneId.systemDefault()).format(instant);
      RefreshRequest refreshRequest = new RefreshRequest(prefix + "*" + date);
      zeebeEsClient.indices().refresh(refreshRequest, RequestOptions.DEFAULT);
    } catch (IOException ex) {
      throw new RuntimeException(ex);
    }
  }

  @Override
  public void finished(Description description) {
    stopZeebe();
    if (client != null) {
      client.close();
      client = null;
    }
    if (!failed) {
      TestUtil.removeAllIndices(zeebeEsClient, prefix);
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
        // retry
      } catch (Exception e) {
        logger.error("Topology cannot be retrieved.");
        e.printStackTrace();
        break;
        // exit
      }
    }
  }
}
