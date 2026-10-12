/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

// @ts-check

import path from 'node:path';
import {fileURLToPath} from 'node:url';

const PACKAGE_ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const SPECS_DIR = path.join(PACKAGE_ROOT, 'specs');
/** The `webapp/client` workspace root. Its `package.json` version follows the release line of the repository. */
const WORKSPACE_ROOT = path.resolve(PACKAGE_ROOT, '..', '..');
const REPO_ROOT = path.resolve(WORKSPACE_ROOT, '..', '..');

export {PACKAGE_ROOT, SPECS_DIR, WORKSPACE_ROOT, REPO_ROOT};
