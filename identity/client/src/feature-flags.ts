/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import { isSaaS } from "./configuration";

export const featureFlags = [];

<<<<<<< HEAD
export const IS_NEW_DESIGN_SYSTEM_ENABLED = isSaaS ? false : true;
=======
export const IS_NEW_DESIGN_SYSTEM_ENABLED = true;
>>>>>>> cdb2eb25 (feat: enable new redesign in Admin)
export const IS_NAV_V2_ENABLED = IS_NEW_DESIGN_SYSTEM_ENABLED;
