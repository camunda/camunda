/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
@Json.Import(Topology.class)
@Json.Import(BrokerInfo.class)
@Json.Import(PartitionInfo.class)
@Json.Import(PublishMessageResponse.class)
@Json.Import(ProcessInstanceEvent.class)
@Json.Import(ProcessInstanceResult.class)
@Json.Import(DeploymentEvent.class)
@Json.Import(Process.class)
@Json.Import(Decision.class)
@Json.Import(DecisionRequirements.class)
@Json.Import(Form.class)
package io.camunda.zeebe.zbctl.serde;

import io.avaje.jsonb.Json;
import io.camunda.zeebe.client.api.response.BrokerInfo;
import io.camunda.zeebe.client.api.response.Decision;
import io.camunda.zeebe.client.api.response.DecisionRequirements;
import io.camunda.zeebe.client.api.response.DeploymentEvent;
import io.camunda.zeebe.client.api.response.Form;
import io.camunda.zeebe.client.api.response.PartitionInfo;
import io.camunda.zeebe.client.api.response.ProcessInstanceEvent;
import io.camunda.zeebe.client.api.response.ProcessInstanceResult;
import io.camunda.zeebe.client.api.response.Process;
import io.camunda.zeebe.client.api.response.PublishMessageResponse;
import io.camunda.zeebe.client.api.response.Topology;
