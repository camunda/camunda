/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.connect.os;

import static io.camunda.search.connect.plugin.util.TestDatabaseCustomHeaderSupplierImpl.KEY_CUSTOM_HEADER;
import static io.camunda.search.connect.plugin.util.TestDatabaseCustomHeaderSupplierImpl.VALUE_CUSTOM_HEADER;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import io.camunda.search.connect.configuration.ConnectConfiguration;
import io.camunda.search.connect.os.json.SearchRequestJacksonJsonpMapperWrapper;
import io.camunda.search.connect.plugin.PluginConfiguration;
import io.camunda.search.connect.plugin.PluginRepository;
import io.camunda.search.connect.plugin.util.TestDatabaseCustomHeaderSupplierImpl;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.hc.client5.http.async.methods.SimpleHttpRequest;
import org.apache.hc.client5.http.async.methods.SimpleHttpResponse;
import org.apache.hc.client5.http.auth.AuthScope;
import org.apache.hc.client5.http.auth.CredentialsProvider;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.async.CloseableHttpAsyncClient;
import org.apache.hc.client5.http.impl.async.HttpAsyncClientBuilder;
import org.apache.hc.client5.http.impl.nio.PoolingAsyncClientConnectionManager;
import org.apache.hc.client5.http.nio.AsyncClientConnectionManager;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.core5.concurrent.FutureCallback;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.nio.ssl.TlsStrategy;
import org.apache.hc.core5.reactor.IOReactorConfig;
import org.apache.hc.core5.util.Timeout;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.opensearch.client.json.jackson.JacksonJsonpMapper;
import org.opensearch.client.transport.aws.AwsSdk2Transport;
import org.opensearch.client.transport.httpclient5.ApacheHttpClient5Transport;
import org.opensearch.client.transport.httpclient5.internal.Node;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;

class OpensearchConnectorTest {

  @RegisterExtension
  // Native OS clients refuse to work while
  // actual HTTP(S) connection is available
  static WireMockExtension osServer =
      WireMockExtension.newInstance()
          .options(WireMockConfiguration.wireMockConfig().dynamicPort())
          .build();

  @Test
  void shouldApplyRequestInterceptorsWithinClasspathForNativeSyncClient() throws Exception {
    final var context = HttpClientContext.create();
    final var configuration = new ConnectConfiguration();
    configuration.setInterceptorPlugins(
        List.of(
            new PluginConfiguration(
                "my-plg", TestDatabaseCustomHeaderSupplierImpl.class.getName(), null)));
    final PluginRepository pluginRepository = new PluginRepository();
    final var connector =
        Mockito.spy(
            new OpensearchConnector(configuration, new ObjectMapper(), null, pluginRepository));
    final var client = connector.createClient();

    // when
    final WireMockRuntimeInfo wmRuntimeInfo = osServer.getRuntimeInfo();
    final var asyncResp =
        getOpensearchApacheClient(((ApacheHttpClient5Transport) client._transport()))
            .execute(
                SimpleHttpRequest.create("GET", wmRuntimeInfo.getHttpBaseUrl()),
                context,
                NoopCallback.INSTANCE);
    try {
      asyncResp.get();
    } catch (final Exception e) {
      // ignore as we don't really care about the outcome
    }

    final var reqWrapper = context.getRequest();

    Assertions.assertThat(reqWrapper.getFirstHeader(KEY_CUSTOM_HEADER).getValue())
        .isEqualTo(VALUE_CUSTOM_HEADER);
  }

  @ParameterizedTest
  @CsvSource({
    "false, false, 'Basic auth even with AWS credentials present'",
    "true, true, 'AWS config used when enabled and credentials present'"
  })
  @DisplayName("Should handle AWS configuration scenarios correctly")
  void shouldHandleAwsConfiguration(
      final boolean credentialsPresent, final boolean isAwsTransport, final String scenarioName) {
    System.setProperty("aws.region", "us-west-1");

    try {
      final var credentialsProvider =
          StaticCredentialsProvider.create(AwsBasicCredentials.create("username", "password"));

      final var connectConfig = new ConnectConfiguration();
      connectConfig.setAwsEnabled(credentialsPresent);

      final var connector =
          new OpensearchConnector(
              connectConfig, new ObjectMapper(), credentialsProvider, new PluginRepository());

      final var client = connector.createClient();

      Assertions.assertThat(client._transport() instanceof AwsSdk2Transport)
          .as("Expected AWS transport for scenario: %s", scenarioName)
          .isEqualTo(isAwsTransport);
    } finally {
      System.clearProperty("aws.region");
    }
  }

  @Test
  void shouldApplyRequestInterceptorsWithinClasspathForNativeAsyncClient() throws Exception {
    final var context = HttpClientContext.create();
    final var configuration = new ConnectConfiguration();
    configuration.setInterceptorPlugins(
        List.of(
            new PluginConfiguration(
                "my-plg", TestDatabaseCustomHeaderSupplierImpl.class.getName(), null)));
    final PluginRepository pluginRepository = new PluginRepository();
    final var connector =
        Mockito.spy(
            new OpensearchConnector(configuration, new ObjectMapper(), null, pluginRepository));
    final var client = connector.createAsyncClient();

    // when
    final WireMockRuntimeInfo wmRuntimeInfo = osServer.getRuntimeInfo();
    final var asyncResp =
        getOpensearchApacheClient(((ApacheHttpClient5Transport) client._transport()))
            .execute(
                SimpleHttpRequest.create("GET", wmRuntimeInfo.getHttpBaseUrl()),
                context,
                NoopCallback.INSTANCE);
    try {
      asyncResp.get();
    } catch (final Exception e) {
      // ignore as we don't really care about the outcome
    }

    final var reqWrapper = context.getRequest();

    Assertions.assertThat(reqWrapper.getFirstHeader(KEY_CUSTOM_HEADER).getValue())
        .isEqualTo(VALUE_CUSTOM_HEADER);
  }

  @Test
  void shouldResolveCredentialsForAllConfiguredUrls() throws Exception {
    final var configuration = new ConnectConfiguration();
    configuration.setUrls(List.of("http://opensearch-1:9205", "http://opensearch-2:9206"));
    configuration.setUsername("user");
    configuration.setPassword("secret");

    final var connector = new OpensearchConnector(configuration);
    final var client = connector.createClient();

    final var asyncClient =
        getOpensearchApacheClient((ApacheHttpClient5Transport) client._transport());
    final var credentialsProvider = getCredentialsProvider(asyncClient);
    final var context = HttpClientContext.create();

    Assertions.assertThat(
            credentialsProvider.getCredentials(
                new AuthScope(new HttpHost("http", "opensearch-1", 9205)), context))
        .isNotNull();
    Assertions.assertThat(
            credentialsProvider.getCredentials(
                new AuthScope(new HttpHost("http", "opensearch-2", 9206)), context))
        .isNotNull();
  }

  @Test
  void shouldNotResolveTheAwsRegionOfTheBuilderWhenAwsIsNotUsed() throws Exception {
    // given - AWS is disabled, so the region must never be asked for
    final var configuration = new ConnectConfiguration();
    final var connector =
        OpensearchConnector.builder()
            .configuration(configuration)
            .awsRegion(
                () -> {
                  throw new IllegalStateException("region must not be resolved");
                })
            .build();

    // when
    try (final var transport = connector.createClient()._transport()) {
      // then
      Assertions.assertThat(transport).isInstanceOf(ApacheHttpClient5Transport.class);
    }
  }

  @Test
  void shouldResolveTheAwsRegionOfTheBuilderWhenAwsIsUsed() {
    // given - AWS is enabled and the credentials resolve
    final var configuration = new ConnectConfiguration();
    configuration.setAwsEnabled(true);
    final var credentials =
        StaticCredentialsProvider.create(AwsBasicCredentials.create("accessKey", "secretKey"));

    // when / then - the region of the builder, not the provider chain's, is the one used
    Assertions.assertThatThrownBy(
            () ->
                OpensearchConnector.builder()
                    .configuration(configuration)
                    .credentialsProvider(credentials)
                    .awsRegion(
                        () -> {
                          throw new IllegalStateException("region of the builder");
                        })
                    .build()
                    .createClient())
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("region of the builder");
  }

  @Test
  void shouldUseAwsRegionAndCredentialsOfTheBuilderForTheAwsTransport() throws Exception {
    // given
    final var configuration = new ConnectConfiguration();
    configuration.setAwsEnabled(true);
    final var credentials =
        StaticCredentialsProvider.create(AwsBasicCredentials.create("accessKey", "secretKey"));

    // when
    try (final var transport =
        OpensearchConnector.builder()
            .configuration(configuration)
            .credentialsProvider(credentials)
            .awsRegion(() -> Region.EU_WEST_1)
            .awsTransportCredentials(credentials)
            .build()
            .createClient()
            ._transport()) {
      // then
      Assertions.assertThat(transport).isInstanceOf(AwsSdk2Transport.class);
    }
  }

  @Test
  void shouldRequireAConfigurationToBuild() {
    // when / then
    Assertions.assertThatThrownBy(() -> OpensearchConnector.builder().build())
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("configuration");
  }

  @Test
  void shouldConnectToTheHostsOfTheBuilderInsteadOfTheConfiguredUrl() throws Exception {
    // given - a configured URL that would never be reached
    final var configuration = new ConnectConfiguration();
    configuration.setUrl("http://192.0.2.1:9200");

    // when
    try (final var transport =
        (ApacheHttpClient5Transport)
            OpensearchConnector.builder()
                .configuration(configuration)
                .httpHosts(() -> new HttpHost[] {new HttpHost("http", "localhost", 9301)})
                .build()
                .createClient()
                ._transport()) {
      // then
      Assertions.assertThat(transport.getNodes().keySet())
          .extracting(Node::getHost)
          .containsExactly(HttpHost.create("http://localhost:9301"));
    }
  }

  @Test
  void shouldResolveTheHostsOfTheBuilderOnlyAfterThePluginsAreLoaded() throws Exception {
    // given - the hosts supplier records whether the plugin had already been loaded
    final var pluginRepository = new PluginRepository();
    final var configuration = new ConnectConfiguration();
    configuration.setInterceptorPlugins(
        List.of(
            new PluginConfiguration(
                "my-plg", TestDatabaseCustomHeaderSupplierImpl.class.getName(), null)));
    final var pluginsLoadedWhenResolved = new AtomicBoolean();
    final var connector =
        OpensearchConnector.builder()
            .configuration(configuration)
            .pluginRepository(pluginRepository)
            .httpHosts(
                () -> {
                  pluginsLoadedWhenResolved.set(!pluginRepository.isEmpty());
                  return new HttpHost[] {new HttpHost("http", "localhost", 9301)};
                })
            .build();
    Assertions.assertThat(pluginsLoadedWhenResolved).isFalse();

    // when
    connector.createClient()._transport().close();

    // then
    Assertions.assertThat(pluginsLoadedWhenResolved).isTrue();
  }

  @Test
  void shouldSerializeSearchRequestsWithTheWrapperMapperByDefault() throws Exception {
    // given
    final var configuration = new ConnectConfiguration();

    // when
    try (final var transport =
        OpensearchConnector.builder()
            .configuration(configuration)
            .build()
            .createClient()
            ._transport()) {
      // then
      Assertions.assertThat(transport.jsonpMapper())
          .isInstanceOf(SearchRequestJacksonJsonpMapperWrapper.class);
    }
  }

  @Test
  void shouldUseTheJsonpMapperOfTheBuilder() throws Exception {
    // given
    final var configuration = new ConnectConfiguration();
    final var jsonpMapper = new JacksonJsonpMapper(new ObjectMapper());

    // when
    try (final var transport =
        OpensearchConnector.builder()
            .configuration(configuration)
            .jsonpMapper(jsonpMapper)
            .build()
            .createClient()
            ._transport()) {
      // then
      Assertions.assertThat(transport.jsonpMapper()).isSameAs(jsonpMapper);
    }
  }

  @Test
  void shouldSetUpTheConnectionManagerWhenTheBuilderProvidesATlsStrategy() {
    // given - neither TLS nor pool limits are configured, only the strategy of the builder
    final var configuration = new ConnectConfiguration();
    final var connector =
        OpensearchConnector.builder()
            .configuration(configuration)
            .tlsStrategy(Mockito.mock(TlsStrategy.class))
            .build();
    final var builder = Mockito.mock(HttpAsyncClientBuilder.class);

    // when
    connector.configureHttpClient(builder, configuration);

    // then
    final var captor = ArgumentCaptor.forClass(AsyncClientConnectionManager.class);
    Mockito.verify(builder).setConnectionManager(captor.capture());
    Assertions.assertThat(captor.getValue())
        .isInstanceOf(PoolingAsyncClientConnectionManager.class);
  }

  @Test
  void shouldConfigureConnectionPoolLimitsWhenSet() {
    // given
    final var configuration = new ConnectConfiguration();
    configuration.setMaxConnections(75);
    configuration.setMaxConnectionsPerRoute(40);
    final var connector =
        new OpensearchConnector(configuration, new ObjectMapper(), null, new PluginRepository());
    final var builder = Mockito.mock(HttpAsyncClientBuilder.class);

    // when
    connector.configureHttpClient(builder, configuration);

    // then
    final var captor = ArgumentCaptor.forClass(AsyncClientConnectionManager.class);
    Mockito.verify(builder).setConnectionManager(captor.capture());
    final var connectionManager = (PoolingAsyncClientConnectionManager) captor.getValue();
    Assertions.assertThat(connectionManager.getMaxTotal()).isEqualTo(75);
    Assertions.assertThat(connectionManager.getDefaultMaxPerRoute()).isEqualTo(40);
  }

  @Test
  void shouldNotConfigureConnectionManagerWhenNothingSet() {
    // given
    final var configuration = new ConnectConfiguration();
    final var connector =
        new OpensearchConnector(configuration, new ObjectMapper(), null, new PluginRepository());
    final var builder = Mockito.mock(HttpAsyncClientBuilder.class);

    // when
    connector.configureHttpClient(builder, configuration);

    // then
    Mockito.verify(builder, Mockito.never()).setConnectionManager(Mockito.any());
  }

  @Test
  void shouldConfigureTimeoutsWhenSet() {
    // given
    final var configuration = new ConnectConfiguration();
    configuration.setSocketTimeout(125456);
    configuration.setConnectTimeout(654321);

    final var connector =
        new OpensearchConnector(configuration, new ObjectMapper(), null, new PluginRepository());
    final var builder = Mockito.mock(RequestConfig.Builder.class);

    // when
    connector.setTimeouts(builder, configuration);

    // then
    Mockito.verify(builder).setResponseTimeout(Timeout.of(125456L, TimeUnit.MILLISECONDS));
    Mockito.verify(builder).setConnectTimeout(Timeout.of(654321L, TimeUnit.MILLISECONDS));
    Mockito.verify(builder)
        .setConnectionRequestTimeout(Timeout.of(180_000L, TimeUnit.MILLISECONDS));
  }

  @Test
  void shouldConfigureDefaultTimeouts() {
    // given
    final var configuration = new ConnectConfiguration();
    final var connector =
        new OpensearchConnector(configuration, new ObjectMapper(), null, new PluginRepository());
    final var builder = Mockito.mock(RequestConfig.Builder.class);

    // when
    connector.setTimeouts(builder, configuration);

    // then
    Mockito.verify(builder).setResponseTimeout(Timeout.of(30_000L, TimeUnit.MILLISECONDS));
    Mockito.verify(builder).setConnectTimeout(Timeout.of(5_000L, TimeUnit.MILLISECONDS));
    Mockito.verify(builder)
        .setConnectionRequestTimeout(Timeout.of(180_000L, TimeUnit.MILLISECONDS));
  }

  @Test
  void shouldApplyTimeoutsWhenCreatingClientWithoutConfiguredTimeouts() throws Exception {
    // given
    final var configuration = new ConnectConfiguration();
    final var connector =
        Mockito.spy(
            new OpensearchConnector(
                configuration, new ObjectMapper(), null, new PluginRepository()));

    // when
    try (final var ignored = connector.createClient()._transport()) {
      // then
      Mockito.verify(connector)
          .setTimeouts(Mockito.any(RequestConfig.Builder.class), Mockito.eq(configuration));
    }
  }

  @Test
  void shouldEnableTcpKeepAlive() {
    // given
    final var configuration = new ConnectConfiguration();
    final var connector =
        new OpensearchConnector(configuration, new ObjectMapper(), null, new PluginRepository());
    final var builder = Mockito.mock(HttpAsyncClientBuilder.class);

    // when
    connector.configureHttpClient(builder, configuration);

    // then
    final var captor = ArgumentCaptor.forClass(IOReactorConfig.class);
    Mockito.verify(builder).setIOReactorConfig(captor.capture());
    Assertions.assertThat(captor.getValue().isSoKeepAlive()).isTrue();
  }

  private static CloseableHttpAsyncClient getOpensearchApacheClient(
      final ApacheHttpClient5Transport client) throws Exception {
    final var field = client.getClass().getDeclaredField("client");
    field.setAccessible(true);
    return (CloseableHttpAsyncClient) field.get(client);
  }

  private static CredentialsProvider getCredentialsProvider(final CloseableHttpAsyncClient client)
      throws Exception {
    Class<?> current = client.getClass();
    while (current != null) {
      for (final var field : current.getDeclaredFields()) {
        if (CredentialsProvider.class.isAssignableFrom(field.getType())) {
          field.setAccessible(true);
          return (CredentialsProvider) field.get(client);
        }
      }
      current = current.getSuperclass();
    }
    throw new IllegalStateException("CredentialsProvider not found on client");
  }

  private static final class NoopCallback implements FutureCallback<SimpleHttpResponse> {
    private static final NoopCallback INSTANCE = new NoopCallback();

    @Override
    public void completed(final SimpleHttpResponse result) {}

    @Override
    public void failed(final Exception ex) {}

    @Override
    public void cancelled() {}
  }
}
