/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {completionErrorMap} from 'v1/api/useCompleteTask.mutation';

const ERRORS_THAT_SHOULD_FETCH_MORE: string[] = [
  completionErrorMap.taskNotAssigned,
  completionErrorMap.taskNotAssignedToCurrentUser,
  completionErrorMap.taskIsNotActive,
];

export {ERRORS_THAT_SHOULD_FETCH_MORE};
