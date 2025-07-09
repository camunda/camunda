/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import * as fs from 'node:fs/promises';
import {join} from 'node:path';

const LICENSE_BANNER = `/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
`;

async function findIndexJsFiles(dir) {
  try {
    const files = await fs.readdir(dir);
    return files.filter(
      (file) => file.startsWith('index') && file.endsWith('.js'),
    );
  } catch (error) {
    if (error.code === 'ENOENT') {
      throw new Error(`Directory ${dir} does not exist`);
    }
    throw error;
  }
}

async function addLicenseBanner() {
  try {
    const buildJsDir = join('build', 'assets');
    const indexJsFiles = await findIndexJsFiles(buildJsDir);

    if (indexJsFiles.length === 0) {
      throw new Error('No index*.js files found in build/assets/');
    }

    for (const file of indexJsFiles) {
      const targetFile = join(buildJsDir, file);
      const data = await fs.readFile(targetFile, 'utf8');
      const output = `${LICENSE_BANNER}${data}`;

      await fs.writeFile(targetFile, output);
    }

    console.log('build successful');
  } catch (error) {
    console.error('Error adding license banner:', error);
    process.exit(1);
  }
}

addLicenseBanner();
