/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import { isNewDesignSystemEnabled } from "./configuration";

export const featureFlags = [];

export const IS_NEW_DESIGN_SYSTEM_ENABLED = isNewDesignSystemEnabled;
export const IS_NAV_V2_ENABLED = IS_NEW_DESIGN_SYSTEM_ENABLED;
