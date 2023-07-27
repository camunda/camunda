/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.shared.management;

import io.camunda.zeebe.protocol.impl.stream.job.JobActivationProperties;
import io.camunda.zeebe.transport.stream.api.ClientStream;
import io.camunda.zeebe.transport.stream.api.RemoteStreamInfo;
import java.util.Collection;

public interface JobStreamEndpointService {
  Collection<RemoteStreamInfo<JobActivationProperties>> remoteJobStreams();

  Collection<ClientStream<JobActivationProperties>> clientJobStreams();
}
