/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import getArgoCdStatus from './getArgoCdStatus';
import getCiResults from './getCiResults';
import getSnykStatus from './getSnykStatus';

async function getDailyStatusMessage() {
  const ciResults = await getCiResults();
  const argocdResults = await getArgoCdStatus();
  const snykResults = await getSnykStatus();

  const message =
    ':github: *GHA status:*\n' +
    ciResults +
    '\n:argocd: *Argo envs status:*\n' +
    argocdResults +
    '\n:snyk: *Snyk status:*\n' +
    snykResults;

  // this has to be output in the console to be fetched by $GITHUB_OUTPUT
  console.log(message);
}
getDailyStatusMessage();
