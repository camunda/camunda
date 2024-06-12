/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import fs from 'node:fs/promises';
import path, {dirname} from 'path';
import {fileURLToPath} from 'url';

const oldPath = path.join(
  dirname(fileURLToPath(import.meta.url)),
  'build',
  'index.prod.html',
);
const newPath = path.join(
  dirname(fileURLToPath(import.meta.url)),
  'build',
  'index.html',
);

fs.rename(oldPath, newPath, (err) => {
  if (err) {
    throw err;
  }

  console.log('Rename complete!');
});
