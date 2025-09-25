/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine;

import io.camunda.zeebe.protocol.record.intent.IncidentIntent;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.intent.UserTaskIntent;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Reflects classes that handle a specific intent */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface HandlesIntent {
  IncidentIntent incident() default IncidentIntent.UNKNOWN;

  IncidentIntent[] incidents() default {};

  JobIntent job() default JobIntent.UNKNOWN;

  JobIntent[] jobs() default {};

  UserTaskIntent userTask() default UserTaskIntent.UNKNOWN;

  UserTaskIntent[] userTasks() default {};
}
