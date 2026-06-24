/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {dirname, resolve} from 'path';
import {existsSync, symlinkSync} from 'fs';
import {fileURLToPath} from 'url';

const __dirname = dirname(fileURLToPath(import.meta.url));

const srcDirectory = resolve(__dirname, '..', 'src');

if (!existsSync(srcDirectory + '/node_modules')) {
  symlinkSync(srcDirectory + '/modules', srcDirectory + '/node_modules', 'dir');
}
