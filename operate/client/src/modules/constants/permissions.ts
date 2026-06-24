/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

function getForbiddenPermissionsError(
  resource: string,
  resourceDescription: string,
) {
  return {
    message: `Missing permissions to access ${resource}`,
    additionalInfo: `Please contact your organization owner or admin to give you the necessary permissions to access ${resourceDescription}`,
  } as const;
}

export {getForbiddenPermissionsError};
