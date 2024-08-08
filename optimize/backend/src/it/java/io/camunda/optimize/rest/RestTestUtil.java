/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.rest;

import jakarta.ws.rs.core.Response;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.OffsetDateTime;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.SneakyThrows;
import org.apache.commons.io.IOUtils;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class RestTestUtil {

  @SneakyThrows
  public static String getResponseContentAsString(final Response response) {
    final byte[] result = getResponseContentAsByteArray(response);
    return new String(result);
  }

  public static byte[] getResponseContentAsByteArray(final Response response) throws IOException {
    final ByteArrayOutputStream bos = new ByteArrayOutputStream();
    IOUtils.copy(response.readEntity(InputStream.class), bos);
    return bos.toByteArray();
  }

  public static double getOffsetDiffInHours(final OffsetDateTime o1, final OffsetDateTime o2) {
    final int offsetDiffInSeconds =
        Math.abs(o1.getOffset().getTotalSeconds() - o2.getOffset().getTotalSeconds());
    return offsetDiffInSeconds / 3600.0; // convert to hours
  }
}
