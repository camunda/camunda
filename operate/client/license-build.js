/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

const fs = require('fs');
const glob = require('glob');

const target = glob.sync('build/static/js/main*.js');

const LICENSE_BANNER = `/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
`;

return fs.readFile(target[0], 'utf8', (err, data) => {
  if (err) {
    throw err;
  }

  const output = `${LICENSE_BANNER}${data}`;

  return fs.writeFile(target[0], output, (err) => {
    if (err) {
      throw err;
    }

    console.log('build successful');
  });
});
