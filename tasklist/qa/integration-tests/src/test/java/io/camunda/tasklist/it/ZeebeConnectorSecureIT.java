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
package io.camunda.tasklist.it;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.tasklist.property.TasklistProperties;
import io.camunda.tasklist.property.ZeebeProperties;
import io.camunda.tasklist.qa.util.ContainerVersionsUtil;
import io.camunda.tasklist.util.CertificateUtil;
import io.camunda.tasklist.zeebe.ZeebeConnector;
import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.client.api.response.BrokerInfo;
import io.zeebe.containers.ZeebeContainer;
import java.io.File;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.testcontainers.containers.wait.strategy.LogMessageWaitStrategy;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

@SpringBootTest(
    classes = {ZeebeConnector.class, TasklistProperties.class},
    properties = {
      TasklistProperties.PREFIX + ".importer.startLoadingDataOnStartup = false",
      TasklistProperties.PREFIX + ".archiver.rolloverEnabled = false",
    })
@ExtendWith(SpringExtension.class)
public class ZeebeConnectorSecureIT {

  private static final String CERTIFICATE_FILE = "zeebe-test-chain.cert.pem";
  private static final String PRIVATE_KEY_FILE = "zeebe-test-server.key.pem";
  private static final DockerImageName ZEEBE_DOCKER_IMAGE =
      DockerImageName.parse("camunda/zeebe")
          .withTag(
              ContainerVersionsUtil.readProperty(
                  ContainerVersionsUtil.ZEEBE_CURRENTVERSION_DOCKER_PROPERTY_NAME));
  @Autowired ZeebeConnector zeebeConnector;
  private ZeebeContainer zeebeContainer;
  private ZeebeClient zeebeClient;

  @Test
  public void shouldConnectWithTLS(@TempDir final File tempDir) throws Exception {
    // given
    final File certFile = new File(tempDir, CERTIFICATE_FILE);
    final File privateKeyFile = new File(tempDir, PRIVATE_KEY_FILE);
    CertificateUtil.generateRSACertificate(certFile, privateKeyFile);
    zeebeContainer =
        new ZeebeContainer(ZEEBE_DOCKER_IMAGE)
            .withCopyFileToContainer(
                MountableFile.forHostPath(tempDir.toPath(), 0755), "/usr/local/zeebe/certs")
            .withEnv(
                Map.of(
                    "ZEEBE_BROKER_GATEWAY_SECURITY_CERTIFICATECHAINPATH",
                    "/usr/local/zeebe/certs/" + CERTIFICATE_FILE,
                    "ZEEBE_BROKER_GATEWAY_SECURITY_PRIVATEKEYPATH",
                    "/usr/local/zeebe/certs/" + PRIVATE_KEY_FILE,
                    "ZEEBE_BROKER_GATEWAY_SECURITY_ENABLED",
                    "true"))
            // Can't use connection wait strategy because of TLS
            .waitingFor(
                new LogMessageWaitStrategy()
                    .withRegEx(".*Broker is ready!.*")
                    .withStartupTimeout(Duration.ofSeconds(101)));
    zeebeContainer.start();
    zeebeClient =
        zeebeConnector.newZeebeClient(
            new ZeebeProperties()
                .setGatewayAddress(zeebeContainer.getExternalGatewayAddress())
                .setSecure(true)
                .setCertificatePath(tempDir.getCanonicalPath() + "/" + CERTIFICATE_FILE));
    // when
    final List<BrokerInfo> brokerInfos =
        zeebeClient.newTopologyRequest().send().join().getBrokers();
    // then
    assertThat(brokerInfos).isNotEmpty();
  }

  @AfterEach
  public void cleanUp() {
    if (zeebeClient != null) {
      zeebeClient.close();
    }
    if (zeebeContainer != null) {
      zeebeContainer.stop();
    }
  }
}
