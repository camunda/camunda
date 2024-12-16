/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {requestAndParse} from 'modules/request';

type License = {
  licenseType: string;
  validLicense: boolean;
  isCommercial: boolean;
  expiresAt: string | null;
};

const fetchLicense = async () => {
  return requestAndParse<License>({
    url: `/v2/license`,
  });
};

export {fetchLicense};
export type {License};
