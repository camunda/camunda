/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {get} from 'request';
import {getFullURL} from '../api';

export async function loadTranslation(version: string, localeCode: string) {
  const response = await get(getFullURL(`api/localization`), {
    version,
    localeCode,
  });

  return await response.json();
}
