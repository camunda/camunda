/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import { ApiDefinition, apiGet } from "../request";
export const LICENSE_ENDPOINT = "/license";

export type License = {
  validLicense: boolean;
  licenseType: string;
  isCommercial: boolean;
  expiresAt: string | null;
};

export const checkLicense: ApiDefinition<License> = () =>
  apiGet(`${LICENSE_ENDPOINT}`);
