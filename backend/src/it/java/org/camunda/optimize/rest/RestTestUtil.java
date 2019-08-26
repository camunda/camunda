/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.rest;

import lombok.experimental.UtilityClass;
import org.apache.commons.io.IOUtils;

import javax.ws.rs.core.Response;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

@UtilityClass
public class RestTestUtil {

  public static String getResponseContentAsString(final Response response) throws IOException {
    byte[] result = getResponseContentAsByteArray(response);
    return new String(result);
  }

  public static byte[] getResponseContentAsByteArray(final Response response) throws IOException {
    ByteArrayOutputStream bos = new ByteArrayOutputStream();
    IOUtils.copy(response.readEntity(InputStream.class), bos);
    return bos.toByteArray();
  }
}
