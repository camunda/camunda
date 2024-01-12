/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.operate.json;

import com.fasterxml.jackson.core.JsonGenerator;
import io.camunda.operate.entities.OperateEntity;
import java.io.IOException;

public interface OperateEntityWriter<E extends OperateEntity<E>> {

  void writeTo(E entity, JsonGenerator jsonGenerator) throws IOException;
}
