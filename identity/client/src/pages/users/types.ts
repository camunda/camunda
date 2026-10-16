/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type { ProvisionedUser } from "src/utility/api/users";

export type { ProvisionedUser };

/** A user without an explicit `active` flag is considered active. */
export const isUserActive = (user: ProvisionedUser): boolean =>
  user.active !== false;
