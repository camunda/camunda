/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import fs from 'fs';
import path from 'path';

const open = (fileName: string) => {
  return fs.readFileSync(path.join(__dirname, fileName)).toString();
};

export {open};
