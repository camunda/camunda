/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.auth;

import static io.camunda.client.protocol.rest.ResourceTypeEnum.AUTHORIZATION;
import static io.camunda.client.protocol.rest.ResourceTypeEnum.USER;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.application.Profile;
import io.camunda.client.CamundaClient;
import io.camunda.client.api.JsonMapper;
import io.camunda.client.impl.CamundaClientImpl;
import io.camunda.client.impl.http.HttpCamundaFuture;
import io.camunda.client.impl.http.HttpClient;
import io.camunda.client.protocol.rest.PermissionTypeEnum;
import io.camunda.it.utils.BrokerITInvocationProvider;
import io.camunda.it.utils.CamundaClientTestFactory.Authenticated;
import io.camunda.it.utils.CamundaClientTestFactory.Permissions;
import io.camunda.it.utils.CamundaClientTestFactory.User;
import io.camunda.zeebe.gateway.protocol.rest.UserFilterRequest;
import io.camunda.zeebe.gateway.protocol.rest.UserSearchQueryRequest;
import io.camunda.zeebe.gateway.protocol.rest.UserSearchResponse;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.apache.hc.client5.http.config.RequestConfig;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.RegisterExtension;

@TestInstance(Lifecycle.PER_CLASS)
class UserAuthorizationIT {

  private static final String ADMIN = "admin";
  private static final String RESTRICTED = "restricted-user";
  private static final String RESTRICTED_WITH_READ = "restricted-user-2";

  private static final User ADMIN_USER =
      new User(
          "admin",
          "password",
          List.of(
              new Permissions(USER, PermissionTypeEnum.CREATE, List.of("*")),
              new Permissions(USER, PermissionTypeEnum.UPDATE, List.of("*")),
              new Permissions(USER, PermissionTypeEnum.READ, List.of("*")),
              new Permissions(AUTHORIZATION, PermissionTypeEnum.UPDATE, List.of("*"))));
  private static final User RESTRICTED_USER =
      new User(
          RESTRICTED,
          "password",
          List.of());

  private static final User RESTRICTED_USER_WITH_READ_PERMISSION =
      new User(
          RESTRICTED_WITH_READ,
          "password",
          List.of(new Permissions(USER, PermissionTypeEnum.READ, List.of("*"))));

  @RegisterExtension
  static final BrokerITInvocationProvider PROVIDER =
      new BrokerITInvocationProvider()
          .withoutRdbmsExporter()
          .withAdditionalProfiles(Profile.AUTH_BASIC)
          .withAuthorizationsEnabled()
          .withUsers(ADMIN_USER, RESTRICTED_USER, RESTRICTED_USER_WITH_READ_PERMISSION);


  @TestTemplate
  void searchUsersShouldReturnEmptyListByFilter(@Authenticated(ADMIN) final CamundaClient adminClient) {
    final CamundaClientImpl userClient1 = (CamundaClientImpl) adminClient;
    final JsonMapper jsonMapper = userClient1.getConfiguration().getJsonMapper();
    final HttpClient httpClient = userClient1.getHttpClient();

    final RequestConfig httpRequestConfig =
        httpClient.newRequestConfig().setResponseTimeout(1500, TimeUnit.MILLISECONDS).build();
    final UserSearchQueryRequest userSearchQueryRequest = new UserSearchQueryRequest();
    userSearchQueryRequest.filter(new UserFilterRequest().username("non-existent-user"));

    final String requestBody = jsonMapper.toJson(userSearchQueryRequest);

    final HttpCamundaFuture<UserSearchResponse> futureResult = new HttpCamundaFuture<>();
    httpClient.post(
        "/users/search",
        requestBody,
        httpRequestConfig,
        UserSearchResponse.class,
        response -> response,
        futureResult);

    final UserSearchResponse result = futureResult.join();
    System.out.println(result);


    assertThat(result.getItems()).hasSize(0);

  }

  @TestTemplate
  void searchUsersShouldReturnListOfAllUsers(@Authenticated(ADMIN) final CamundaClient adminClient) {
    final CamundaClientImpl userClient1 = (CamundaClientImpl) adminClient;
    final JsonMapper jsonMapper = userClient1.getConfiguration().getJsonMapper();
    final HttpClient httpClient = userClient1.getHttpClient();

    final RequestConfig httpRequestConfig =
        httpClient.newRequestConfig().setResponseTimeout(15000, TimeUnit.MILLISECONDS).build();
    final UserSearchQueryRequest userSearchQueryRequest = new UserSearchQueryRequest();

    final String requestBody = jsonMapper.toJson(userSearchQueryRequest);

    final HttpCamundaFuture<UserSearchResponse> futureResult = new HttpCamundaFuture<>();
    httpClient.post(
        "/users/search",
        requestBody,
        httpRequestConfig,
        UserSearchResponse.class,
        response -> response,
        futureResult);

    final UserSearchResponse result = futureResult.join();


    assertThat(result.getItems()).hasSize(3);

  }


  @TestTemplate
  void searchShouldReturnEmptyListInsufficientAuthorizations(
      @Authenticated(RESTRICTED) final CamundaClient userClient) {

    final CamundaClientImpl userClient1 = (CamundaClientImpl) userClient;
    final JsonMapper jsonMapper = userClient1.getConfiguration().getJsonMapper();
    final HttpClient httpClient = userClient1.getHttpClient();

    final RequestConfig httpRequestConfig =
        httpClient.newRequestConfig().setResponseTimeout(15000, TimeUnit.MILLISECONDS).build();

    final UserSearchQueryRequest userSearchQueryRequest = new UserSearchQueryRequest();

    final String requestBody = jsonMapper.toJson(userSearchQueryRequest);

    final HttpCamundaFuture<UserSearchResponse> futureResult = new HttpCamundaFuture<>();
    httpClient.post(
        "/users/search",
        requestBody,
        httpRequestConfig,
        UserSearchResponse.class,
        response -> response,
        futureResult);

    final UserSearchResponse result = futureResult.join();
    //todo delete this
    System.out.println(result);

    userClient.newCreateAuthorizationCommand();

    //todo should return empty list , but return restricted user
    assertThat(result.getItems()).hasSize(0);

  }

  @TestTemplate
  void searchShouldReturnListOfUsersIfHaveReadPermission(
      @Authenticated(RESTRICTED_WITH_READ) final CamundaClient userClient) {

    final CamundaClientImpl userClient1 = (CamundaClientImpl) userClient;
    final JsonMapper jsonMapper = userClient1.getConfiguration().getJsonMapper();
    final HttpClient httpClient = userClient1.getHttpClient();

    final RequestConfig httpRequestConfig =
        httpClient.newRequestConfig().setResponseTimeout(15000, TimeUnit.MILLISECONDS).build();

    final UserSearchQueryRequest userSearchQueryRequest = new UserSearchQueryRequest();

    final String requestBody = jsonMapper.toJson(userSearchQueryRequest);

    final HttpCamundaFuture<UserSearchResponse> futureResult = new HttpCamundaFuture<>();
    httpClient.post(
        "/users/search",
        requestBody,
        httpRequestConfig,
        UserSearchResponse.class,
        response -> response,
        futureResult);

    final UserSearchResponse result = futureResult.join();
    //todo delete this
    System.out.println(result);

    userClient.newCreateAuthorizationCommand();

    //todo should return all restricted users, as have read permissions
    assertThat(result.getItems()).hasSize(2);

  }

}
