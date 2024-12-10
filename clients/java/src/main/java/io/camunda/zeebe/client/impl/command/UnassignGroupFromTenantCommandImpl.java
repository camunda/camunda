package io.camunda.zeebe.client.impl.command;

import io.camunda.zeebe.client.api.ZeebeFuture;
import io.camunda.zeebe.client.api.command.FinalCommandStep;
import io.camunda.zeebe.client.api.command.UnassignGroupFromTenantCommandStep1;
import io.camunda.zeebe.client.api.response.UnassignGroupFromTenantResponse;
import io.camunda.zeebe.client.impl.http.HttpClient;
import io.camunda.zeebe.client.impl.http.HttpZeebeFuture;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.apache.hc.client5.http.config.RequestConfig;

public final class UnassignGroupFromTenantCommandImpl
    implements UnassignGroupFromTenantCommandStep1 {
  private final HttpClient httpClient;
  private final RequestConfig.Builder httpRequestConfig;
  private final long tenantKey;
  private long groupKey;

  public UnassignGroupFromTenantCommandImpl(final HttpClient httpClient, final long tenantKey) {
    this.httpClient = httpClient;
    httpRequestConfig = httpClient.newRequestConfig();
    this.tenantKey = tenantKey;
  }

  @Override
  public UnassignGroupFromTenantCommandStep1 groupKey(final long groupKey) {
    this.groupKey = groupKey;
    return this;
  }

  @Override
  public FinalCommandStep<UnassignGroupFromTenantResponse> requestTimeout(
      final Duration requestTimeout) {
    httpRequestConfig.setResponseTimeout(requestTimeout.toMillis(), TimeUnit.MILLISECONDS);
    return this;
  }

  @Override
  public ZeebeFuture<UnassignGroupFromTenantResponse> send() {
    final HttpZeebeFuture<UnassignGroupFromTenantResponse> result = new HttpZeebeFuture<>();
    final String endpoint = String.format("/tenants/%d/groups/%d", tenantKey, groupKey);
    httpClient.delete(endpoint, httpRequestConfig.build(), result);
    return result;
  }
}
