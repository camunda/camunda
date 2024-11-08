/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {License} from 'modules/types';

const validLicense: License = {
  validLicense: true,
  licenseType: 'production',
  isCommercial: false,
  expiresAt: new Date().toISOString(),
};

const saasLicense: License = {
  validLicense: true,
  licenseType: 'saas',
  isCommercial: false,
  expiresAt: new Date().toISOString(),
};

const invalidLicense: License = {
  validLicense: false,
  licenseType: 'production',
  isCommercial: false,
  expiresAt: null,
};

const commercialExpired: License = {
  validLicense: true,
  licenseType: 'production',
  isCommercial: true,
  expiresAt: new Date().toISOString(),
};

const date = new Date();
date.setUTCDate(date.getUTCDate() + 1); // add one day

const validNonCommercial = {
  validLicense: true,
  licenseType: 'production',
  isCommercial: false,
  expiresAt: date.toISOString(),
};

export {
  validLicense,
  invalidLicense,
  saasLicense,
  commercialExpired,
  validNonCommercial,
};
