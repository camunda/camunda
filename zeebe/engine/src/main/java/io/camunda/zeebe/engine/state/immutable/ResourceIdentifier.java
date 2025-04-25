/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.immutable;

import io.camunda.zeebe.engine.state.immutable.DecisionState.DecisionRequirementsIdentifier;
import io.camunda.zeebe.engine.state.immutable.FormState.FormIdentifier;
import io.camunda.zeebe.engine.state.immutable.ProcessState.ProcessIdentifier;

public sealed interface ResourceIdentifier
    permits ProcessIdentifier, FormIdentifier, DecisionRequirementsIdentifier {}
