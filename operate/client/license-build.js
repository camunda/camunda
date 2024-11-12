/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

const fs = require('fs');
const glob = require('glob');

const target = glob.sync('build/static/js/main*.js');

const LICENSE_BANNER = `/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
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
