/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import path from 'node:path';
import {fileURLToPath} from 'node:url';

const AUTH_DIR = path.join(path.dirname(fileURLToPath(import.meta.url)), '..', '.auth');

export function storageStatePath(name: string): string {
  return path.join(AUTH_DIR, `${name}.json`);
}
