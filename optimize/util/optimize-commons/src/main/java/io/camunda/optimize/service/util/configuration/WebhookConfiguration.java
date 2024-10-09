/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.util.configuration;

import io.camunda.optimize.dto.optimize.alert.AlertNotificationDto;
import java.util.Map;
import java.util.function.Function;

public class WebhookConfiguration {

  private String url;
  private Map<String, String> headers;
  private String httpMethod;
  private String defaultPayload;
  private ProxyConfiguration proxy;

  public WebhookConfiguration() {}

  public String getUrl() {
    return url;
  }

  public void setUrl(final String url) {
    this.url = url;
  }

  public Map<String, String> getHeaders() {
    return headers;
  }

  public void setHeaders(final Map<String, String> headers) {
    this.headers = headers;
  }

  public String getHttpMethod() {
    return httpMethod;
  }

  public void setHttpMethod(final String httpMethod) {
    this.httpMethod = httpMethod;
  }

  public String getDefaultPayload() {
    return defaultPayload;
  }

  public void setDefaultPayload(final String defaultPayload) {
    this.defaultPayload = defaultPayload;
  }

  public ProxyConfiguration getProxy() {
    return proxy;
  }

  public void setProxy(final ProxyConfiguration proxy) {
    this.proxy = proxy;
  }

  protected boolean canEqual(final Object other) {
    return other instanceof WebhookConfiguration;
  }

  @Override
  public int hashCode() {
    final int PRIME = 59;
    int result = 1;
    final Object $url = getUrl();
    result = result * PRIME + ($url == null ? 43 : $url.hashCode());
    final Object $headers = getHeaders();
    result = result * PRIME + ($headers == null ? 43 : $headers.hashCode());
    final Object $httpMethod = getHttpMethod();
    result = result * PRIME + ($httpMethod == null ? 43 : $httpMethod.hashCode());
    final Object $defaultPayload = getDefaultPayload();
    result = result * PRIME + ($defaultPayload == null ? 43 : $defaultPayload.hashCode());
    final Object $proxy = getProxy();
    result = result * PRIME + ($proxy == null ? 43 : $proxy.hashCode());
    return result;
  }

  @Override
  public boolean equals(final Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof WebhookConfiguration)) {
      return false;
    }
    final WebhookConfiguration other = (WebhookConfiguration) o;
    if (!other.canEqual((Object) this)) {
      return false;
    }
    final Object this$url = getUrl();
    final Object other$url = other.getUrl();
    if (this$url == null ? other$url != null : !this$url.equals(other$url)) {
      return false;
    }
    final Object this$headers = getHeaders();
    final Object other$headers = other.getHeaders();
    if (this$headers == null ? other$headers != null : !this$headers.equals(other$headers)) {
      return false;
    }
    final Object this$httpMethod = getHttpMethod();
    final Object other$httpMethod = other.getHttpMethod();
    if (this$httpMethod == null
        ? other$httpMethod != null
        : !this$httpMethod.equals(other$httpMethod)) {
      return false;
    }
    final Object this$defaultPayload = getDefaultPayload();
    final Object other$defaultPayload = other.getDefaultPayload();
    if (this$defaultPayload == null
        ? other$defaultPayload != null
        : !this$defaultPayload.equals(other$defaultPayload)) {
      return false;
    }
    final Object this$proxy = getProxy();
    final Object other$proxy = other.getProxy();
    if (this$proxy == null ? other$proxy != null : !this$proxy.equals(other$proxy)) {
      return false;
    }
    return true;
  }

  @Override
  public String toString() {
    return "WebhookConfiguration(url="
        + getUrl()
        + ", headers="
        + getHeaders()
        + ", httpMethod="
        + getHttpMethod()
        + ", defaultPayload="
        + getDefaultPayload()
        + ", proxy="
        + getProxy()
        + ")";
  }

  public enum Placeholder {
    // This only works as the link is at the end of the composed text. We would need to refactor
    // this if the webhook
    // structure of alerts changes in future
    ALERT_MESSAGE(
        alertNotificationDto -> alertNotificationDto.getAlertMessage() + "&utm_medium=webhook"),
    ALERT_NAME(notificationDto -> notificationDto.getAlert().getName()),
    ALERT_REPORT_LINK(
        alertNotificationDto -> alertNotificationDto.getReportLink() + "&utm_medium=webhook"),
    ALERT_CURRENT_VALUE(notificationDto -> String.valueOf(notificationDto.getCurrentValue())),
    ALERT_THRESHOLD_VALUE(
        notificationDto -> String.valueOf(notificationDto.getAlert().getThreshold())),
    ALERT_THRESHOLD_OPERATOR(
        notificationDto -> notificationDto.getAlert().getThresholdOperator().getId()),
    ALERT_TYPE(notificationDto -> notificationDto.getType().getId()),
    ALERT_INTERVAL(
        notificationDto ->
            String.valueOf(notificationDto.getAlert().getCheckInterval().getValue())),
    ALERT_INTERVAL_UNIT(
        notificationDto -> notificationDto.getAlert().getCheckInterval().getUnit().getId()),
    ;

    private static final String PLACEHOLDER_TEMPLATE = "{{%s}}";

    private final Function<AlertNotificationDto, String> valueExtractor;

    Placeholder(final Function<AlertNotificationDto, String> valueExtractor) {
      this.valueExtractor = valueExtractor;
    }

    public String getPlaceholderString() {
      return String.format(PLACEHOLDER_TEMPLATE, name());
    }

    public String extractValue(final AlertNotificationDto notificationDto) {
      return valueExtractor.apply(notificationDto);
    }
  }
}
