/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.sharing;

import java.io.Serializable;
import lombok.Data;

@Data
public class ReportShareRestDto implements Serializable {

  private String id;
  private String reportId;

  public static final class Fields {

    public static final String id = "id";
    public static final String reportId = "reportId";
  }
}
