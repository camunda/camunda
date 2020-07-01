/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.rest;

import org.camunda.optimize.AbstractIT;
import org.camunda.optimize.dto.optimize.query.collection.CollectionDefinitionRestDto;
import org.camunda.optimize.service.security.util.LocalDateUtil;
import org.junit.jupiter.api.Test;

import javax.ws.rs.core.Response;
import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.rest.RestTestUtil.getOffsetDiffInHours;
import static org.camunda.optimize.rest.constants.RestConstants.X_OPTIMIZE_CLIENT_TIMEZONE;

public class TimeZoneAdjustmentRestServiceIT extends AbstractIT {

  @Test
  public void unknownTimezoneUsesServerTimezone() {
    //given
    OffsetDateTime now = OffsetDateTime.now();
    LocalDateUtil.setCurrentTime(now);
    final String collectionId = collectionClient.createNewCollection();

    // when
    CollectionDefinitionRestDto collection = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildGetCollectionRequest(collectionId)
      .addSingleHeader(X_OPTIMIZE_CLIENT_TIMEZONE, "unknownTimezone")
      .execute(CollectionDefinitionRestDto.class, Response.Status.OK.getStatusCode());

    // then
    assertThat(collection).isNotNull();
    assertThat(collection.getCreated()).isEqualTo(now);
    assertThat(collection.getLastModified()).isEqualTo(now);
    assertThat(getOffsetDiffInHours(collection.getCreated(), now)).isZero();
    assertThat(getOffsetDiffInHours(collection.getLastModified(), now)).isZero();
  }

  @Test
  public void omittedTimezoneUsesServerTimezone() {
    //given
    OffsetDateTime now = OffsetDateTime.now();
    LocalDateUtil.setCurrentTime(now);
    final String collectionId = collectionClient.createNewCollection();

    // when
    CollectionDefinitionRestDto collection = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildGetCollectionRequest(collectionId)
      .execute(CollectionDefinitionRestDto.class, Response.Status.OK.getStatusCode());

    // then
    assertThat(collection).isNotNull();
    assertThat(collection.getCreated()).isEqualTo(now);
    assertThat(collection.getLastModified()).isEqualTo(now);
    assertThat(getOffsetDiffInHours(collection.getCreated(), now)).isZero();
    assertThat(getOffsetDiffInHours(collection.getLastModified(), now)).isZero();
  }
}
