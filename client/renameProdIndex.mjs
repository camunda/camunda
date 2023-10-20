/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
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
