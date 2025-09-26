/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.logstreams.impl.flowcontrol;

import io.camunda.zeebe.protocol.record.intent.CommandDistributionIntent;
import io.camunda.zeebe.protocol.record.intent.DeploymentDistributionIntent;
import io.camunda.zeebe.protocol.record.intent.DeploymentIntent;
import io.camunda.zeebe.protocol.record.intent.Intent;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
<<<<<<< HEAD
import io.camunda.zeebe.protocol.record.intent.scaling.ScaleIntent;
=======
>>>>>>> e395c6a6 (fix: do not enforce write limits for whitelisted commands)
import java.util.Set;

public class WhiteListedCommands {

  private static final Set<? extends Intent> WHITE_LISTED_COMMANDS =
      Set.of(
          JobIntent.COMPLETE,
          JobIntent.FAIL,
<<<<<<< HEAD
          JobIntent.YIELD,
=======
>>>>>>> e395c6a6 (fix: do not enforce write limits for whitelisted commands)
          ProcessInstanceIntent.CANCEL,
          DeploymentIntent.CREATE,
          DeploymentIntent.DISTRIBUTE,
          DeploymentDistributionIntent.COMPLETE,
<<<<<<< HEAD
          ScaleIntent.STATUS,
=======
>>>>>>> e395c6a6 (fix: do not enforce write limits for whitelisted commands)
          CommandDistributionIntent.ACKNOWLEDGE);

  public static boolean isWhitelisted(final Intent intent) {
    return WHITE_LISTED_COMMANDS.contains(intent);
  }
}
