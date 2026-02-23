/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {CurrentUser} from '@camunda/camunda-api-zod-schemas/8.9';

function isForbidden(user: CurrentUser | undefined) {
  return (
    Array.isArray(user?.authorizedComponents) &&
    !user.authorizedComponents.includes('tasklist') &&
    !user.authorizedComponents.includes('*')
  );
}

export {isForbidden};
