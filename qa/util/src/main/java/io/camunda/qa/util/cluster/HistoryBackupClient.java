/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.qa.util.cluster;

import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import feign.Body;
import feign.Feign;
import feign.Headers;
import feign.Param;
import feign.RequestLine;
import feign.Retryer;
import feign.Target.HardCodedTarget;
import feign.jackson.JacksonDecoder;
import feign.jackson.JacksonEncoder;
import io.camunda.management.backups.TakeBackupHistoryResponse;
import io.camunda.webapps.backup.GetBackupStateResponseDto;
import io.camunda.zeebe.qa.util.cluster.TestApplication;
import java.util.List;

public interface HistoryBackupClient {

  @RequestLine("POST")
  @Headers({"Content-Type: application/json", "Accept: application/json"})
  @Body("%7B\"backupId\": \"{backupId}\"%7D")
  TakeBackupHistoryResponse takeBackup(@Param final long backupId);

  @RequestLine("GET /{id}")
  @Headers({"Content-Type: application/json", "Accept: application/json"})
  GetBackupStateResponseDto getBackup(@Param final long id);

  @RequestLine("GET ?verbose={verbose}")
  @Headers({"Content-Type: application/json", "Accept: application/json"})
  List<GetBackupStateResponseDto> getBackups(@Param final boolean verbose);

  /**
   * Returns a {@link HistoryBackupClient} instance using the given node as upstream.
   *
   * @param node the node to connect to
   * @return a new instance of {@link HistoryBackupClient}
   */
  static HistoryBackupClient of(final TestApplication<?> node) {
    return of(node.actuatorUri("backupHistory").toString());
  }

  /**
   * Returns a {@link HistoryBackupClient} instance using the given endpoint as upstream. The
   * endpoint is expected to be a complete absolute URL, e.g.
   * "http://localhost:9600/actuator/backups".
   *
   * @param endpoint the actuator URL to connect to
   * @return a new instance of {@link HistoryBackupClient}
   */
  static HistoryBackupClient of(final String endpoint) {
    final var target = new HardCodedTarget<>(HistoryBackupClient.class, endpoint);
    final var decoder = new JacksonDecoder(List.of(new Jdk8Module(), new JavaTimeModule()));

    return Feign.builder()
        .encoder(new JacksonEncoder())
        .decoder(decoder)
        .retryer(Retryer.NEVER_RETRY)
        .target(target);
  }
}
