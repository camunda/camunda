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
package io.camunda.operate.schema.util.elasticsearch;

import io.camunda.operate.conditions.ElasticsearchCondition;
import io.camunda.operate.connect.ElasticsearchConnector;
import io.camunda.operate.property.ElasticsearchProperties;
import io.camunda.operate.schema.util.ObservableConnector;
import io.camunda.operate.schema.util.ObservableConnector.OperateTestHttpRequest;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import org.apache.http.HttpException;
import org.apache.http.HttpRequest;
import org.apache.http.HttpRequestInterceptor;
import org.apache.http.RequestLine;
import org.apache.http.impl.nio.client.HttpAsyncClientBuilder;
import org.apache.http.protocol.HttpContext;
import org.springframework.context.annotation.Conditional;

@Conditional(ElasticsearchCondition.class)
public class TestElasticsearchConnector extends ElasticsearchConnector
    implements ObservableConnector {

  private final List<Consumer<OperateTestHttpRequest>> requestListeners = new ArrayList<>();

  /**
   * Adds a request interceptor that a test case can plug in, so that we can assert requests made to
   * Elasticsearch
   */
  @Override
  protected HttpAsyncClientBuilder configureHttpClient(
      final HttpAsyncClientBuilder httpAsyncClientBuilder,
      final ElasticsearchProperties elsConfig) {
    httpAsyncClientBuilder.addInterceptorFirst(
        new HttpRequestInterceptor() {

          @Override
          public void process(final HttpRequest request, final HttpContext context)
              throws HttpException, IOException {
            final RequestLine requestLine = request.getRequestLine();

            requestListeners.forEach(
                listener ->
                    listener.accept(
                        new OperateTestHttpRequest() {

                          @Override
                          public String getUri() {
                            return requestLine.getUri();
                          }

                          @Override
                          public String getMethod() {
                            return requestLine.getMethod();
                          }
                        }));
          }
        });
    return super.configureHttpClient(httpAsyncClientBuilder, elsConfig);
  }

  @Override
  public void addRequestListener(final Consumer<OperateTestHttpRequest> listener) {
    requestListeners.add(listener);
  }

  @Override
  public void clearRequestListeners() {
    requestListeners.clear();
  }
}
