/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import fs from 'node:fs';

const sessionFile = 'playwright/.auth/session.json';

const readSessionStorage = (): Record<string, string> => {
  if (!fs.existsSync(sessionFile)) {
    return {};
  }

  return JSON.parse(fs.readFileSync(sessionFile, 'utf-8')) as Record<
    string,
    string
  >;
};

const getCsrfToken = (): string | undefined => {
  return readSessionStorage()['X-CSRF-TOKEN'];
};

export {sessionFile, readSessionStorage, getCsrfToken};
