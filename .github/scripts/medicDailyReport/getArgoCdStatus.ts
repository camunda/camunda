/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import fetchUrl from './fetchUrl';

type Application = {
  status: {health: {status: string}};
  metadata: {name: string};
};

type ApplicationsResponse = {
  items: Application[];
};

export default async function getArgoCdStatus(): Promise<string> {
  const optimizeApplications = (await fetchArgoCdAplications()).items;

  const message = optimizeApplications
    .map(
      (application, idx) =>
        `${idx + 1} ${getApplicationStatus(
          application,
        )} <https://argocd.int.camunda.com/applications/argocd/${
          application.metadata.name
        }|${getApplicationLabel(application)}>`,
    )
    .join('\n');

  return message;
}

async function fetchArgoCdAplications() {
  return fetchUrl<ApplicationsResponse>(
    'https://argocd.int.camunda.com/api/v1/applications?projects=optimize-previews',
    `Bearer ${process.env.ARGOCD_TOKEN}`,
  );
}

function getApplicationStatus(application: Application) {
  return application.status.health.status === 'Healthy' ? '🟢' : '🔴';
}

function getApplicationLabel(application: Application) {
  return application.metadata.name.includes('c8') ? 'C8' : 'C7';
}
