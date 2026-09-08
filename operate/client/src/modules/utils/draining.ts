/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

const DRAINING_MESSAGES = {
  allVersions:
    'One or more versions of this process definition are scheduled for deletion. They stay until their running instances finish, then are removed automatically.',
  version:
    'This process definition version is scheduled for deletion and will be removed automatically once all of its running instances finish.',
} as const;

export {DRAINING_MESSAGES};
