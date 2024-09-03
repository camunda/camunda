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
package io.camunda.operate.connect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.operate.property.OperateOpensearchProperties;
import io.camunda.operate.property.OperateProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opensearch.client.opensearch.OpenSearchAsyncClient;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.transport.aws.AwsSdk2Transport;
import org.opensearch.client.transport.httpclient5.ApacheHttpClient5Transport;

@ExtendWith(MockitoExtension.class)
public class OpensearchConnectorTest {

  private static final String AWS_REGION = "eu-west-1";
  private static final String AWS_SECRET_ACCESS_KEY = "awsSecretAcessKey";
  private static final String AWS_ACCESS_KEY_ID = "awsAccessKeyId";

  ObjectMapper objectMapper = new ObjectMapper();

  private final OperateOpensearchProperties opensearchProperties =
      new OperateOpensearchProperties();

  private final OperateProperties operateProperties = new OperateProperties();

  private OpensearchConnector opensearchConnector;

  @BeforeEach
  public void setup() {
    operateProperties.setOpensearch(opensearchProperties);
    opensearchConnector = new OpensearchConnector(operateProperties, objectMapper);
  }

  @Test
  public void asyncHasAwsEnabledAndAwsCredentialsSetAndShouldUseAwsCredentials() {
    System.setProperty("aws.accessKeyId", AWS_ACCESS_KEY_ID);
    System.setProperty("aws.secretAccessKey", AWS_SECRET_ACCESS_KEY);
    System.setProperty("aws.region", AWS_REGION);
    opensearchProperties.setUsername("demo");
    opensearchProperties.setPassword("demo");
    opensearchProperties.setAwsEnabled(true);

    final OpenSearchAsyncClient client =
        opensearchConnector.createAsyncOsClient(opensearchProperties);

    assertEquals(AwsSdk2Transport.class, client._transport().getClass());
  }

  @Test
  public void asyncHasAwsCredentialsButShouldUseBasicAuth() {
    System.setProperty("aws.accessKeyId", AWS_ACCESS_KEY_ID);
    System.setProperty("aws.secretAccessKey", AWS_SECRET_ACCESS_KEY);
    System.setProperty("aws.region", AWS_REGION);
    opensearchProperties.setUsername("demo");
    opensearchProperties.setPassword("demo");
    // awsEnabled not set -> should default to false
    final OpensearchConnector spyConnector = spy(opensearchConnector);
    doReturn(true).when(spyConnector).checkHealth(any(OpenSearchAsyncClient.class));

    final OpenSearchAsyncClient client = spyConnector.createAsyncOsClient(opensearchProperties);

    assertEquals(ApacheHttpClient5Transport.class, client._transport().getClass());
  }

  @Test
  public void syncHasAwsEnabledAndAwsCredentialsSetAndShouldUseAwsCredentials() {
    System.setProperty("aws.accessKeyId", AWS_ACCESS_KEY_ID);
    System.setProperty("aws.secretAccessKey", AWS_SECRET_ACCESS_KEY);
    System.setProperty("aws.region", AWS_REGION);
    opensearchProperties.setUsername("demo");
    opensearchProperties.setPassword("demo");
    opensearchProperties.setAwsEnabled(true);

    final OpenSearchClient client = opensearchConnector.createOsClient(opensearchProperties);

    assertEquals(AwsSdk2Transport.class, client._transport().getClass());
  }

  @Test
  public void syncHasAwsCredentialsButShouldUseBasicAuth() {
    System.setProperty("aws.accessKeyId", AWS_ACCESS_KEY_ID);
    System.setProperty("aws.secretAccessKey", AWS_SECRET_ACCESS_KEY);
    System.setProperty("aws.region", AWS_REGION);
    opensearchProperties.setUsername("demo");
    opensearchProperties.setPassword("demo");
    // awsEnabled not set -> should default to false
    final OpensearchConnector spyConnector = spy(opensearchConnector);
    doReturn(true).when(spyConnector).checkHealth(any(OpenSearchClient.class));

    final OpenSearchClient client = spyConnector.createOsClient(opensearchProperties);

    assertEquals(ApacheHttpClient5Transport.class, client._transport().getClass());
  }
}
