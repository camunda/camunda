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

import io.camunda.zeebe.client.ZeebeClient;
import io.zeebe.containers.ZeebeContainer;
import java.time.Instant;
import org.junit.rules.TestWatcher;
import org.junit.runner.Description;
import org.springframework.beans.factory.annotation.Autowired;

public class OperateZeebeRule extends TestWatcher {
  @Autowired public OperateZeebeRuleProvider operateZeebeRuleProvider;

  @Override
  public void starting(Description description) {
    operateZeebeRuleProvider.starting(description);
  }

  public void updateRefreshInterval(String value) {
    operateZeebeRuleProvider.updateRefreshInterval(value);
  }

  public void refreshIndices(Instant instant) {
    operateZeebeRuleProvider.refreshIndices(instant);
  }

  @Override
  public void finished(Description description) {
    operateZeebeRuleProvider.finished(description);
  }

  @Override
  protected void failed(Throwable e, Description description) {
    operateZeebeRuleProvider.failed(e, description);
  }

  /**
   * Starts the broker and the client. This is blocking and will return once the broker is ready to
   * accept commands.
   *
   * @throws IllegalStateException if no exporter has previously been configured
   */
  public void startZeebe() {
    operateZeebeRuleProvider.startZeebe();
  }

  /** Stops the broker and destroys the client. Does nothing if not started yet. */
  public void stop() {
    operateZeebeRuleProvider.stopZeebe();
  }

  public String getPrefix() {
    return operateZeebeRuleProvider.getPrefix();
  }

  //  public void setPrefix(String prefix) {
  //    this.prefix = prefix;
  //  }
  //
  public ZeebeContainer getZeebeContainer() {
    return operateZeebeRuleProvider.getZeebeContainer();
  }

  //  public void setOperateProperties(final OperateProperties operateProperties) {
  //    this.operateProperties = operateProperties;
  //  }
  //
  //  public void setZeebeEsClient(final RestHighLevelClient zeebeEsClient) {
  //    this.zeebeEsClient = zeebeEsClient;
  //  }
  //
  public ZeebeClient getClient() {
    return operateZeebeRuleProvider.getClient();
  }
}
