package io.camunda.client.impl.command;

import io.camunda.client.api.CamundaFuture;
import io.camunda.client.api.command.AssignUserToRoleCommandStep1;
import io.camunda.client.api.command.FinalCommandStep;
import io.camunda.client.api.response.AssignUserToRoleResponse;
import io.camunda.client.impl.http.HttpCamundaFuture;
import io.camunda.client.impl.http.HttpClient;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.apache.hc.client5.http.config.RequestConfig;

public final class AssignUserToRoleCommandImpl implements AssignUserToRoleCommandStep1 {

  private final String roleId;
  private String username;
  private final HttpClient httpClient;
  private final RequestConfig.Builder httpRequestConfig;

  public AssignUserToRoleCommandImpl(final HttpClient httpClient, final String roleId) {
    this.httpClient = httpClient;
    this.roleId = roleId;
    httpRequestConfig = httpClient.newRequestConfig();
  }

  @Override
  public AssignUserToRoleCommandStep1 username(final String username) {
    this.username = username;
    return this;
  }

  @Override
  public FinalCommandStep<AssignUserToRoleResponse> requestTimeout(final Duration requestTimeout) {
    httpRequestConfig.setResponseTimeout(requestTimeout.toMillis(), TimeUnit.MILLISECONDS);
    return this;
  }

  @Override
  public CamundaFuture<AssignUserToRoleResponse> send() {
    ArgumentUtil.ensureNotNullNorEmpty("roleId", roleId);
    ArgumentUtil.ensureNotNullNorEmpty("username", username);
    final HttpCamundaFuture<AssignUserToRoleResponse> result = new HttpCamundaFuture<>();
    final String endpoint = String.format("/roles/%s/users/%s", roleId, username);
    httpClient.put(endpoint, null, httpRequestConfig.build(), result);
    return result;
  }
}
