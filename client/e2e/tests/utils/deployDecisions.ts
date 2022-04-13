/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {exec} from 'child_process';
const {ZBCTL_PATH, ZEEBE_GATEWAY_ADDRESS} = process.env;

function deployDecisions() {
  if (ZBCTL_PATH === undefined) {
    console.error(
      '\nPlease provide the path to zbctl with the env variable ZBCTL_PATH and run the script again.\n\nYou can download a zbctl binary from the Zeebe GitHub releases page: https://github.com/camunda-cloud/zeebe/releases\nor install via npm install -g zbctl.\n'
    );
    process.exit(1);
  }

  if (ZEEBE_GATEWAY_ADDRESS === undefined) {
    console.error(
      '\nPlease provide the zeebe gateway address with the env variable ZEEBE_GATEWAY_ADDRESS and run the script again\n'
    );
    process.exit(1);
  }

  exec(
    `${ZBCTL_PATH} --address ${ZEEBE_GATEWAY_ADDRESS} --insecure deploy resource ./e2e/tests/resources/invoiceBusinessDecisions.dmn`,
    (error, stdout, stderr) => {
      if (error !== null) {
        console.log(error, stderr);
        process.exit(1);
      } else {
        console.log(stdout);
      }
    }
  );
}

export {deployDecisions};
