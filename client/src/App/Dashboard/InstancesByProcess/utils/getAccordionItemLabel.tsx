/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import pluralSuffix from 'modules/utils/pluralSuffix';

function getAccordionItemLabel({
  name,
  instancesCount,
  version,
  tenant,
}: {
  name: string;
  instancesCount: number;
  version: number;
  tenant?: string;
}) {
  return `${name} – ${pluralSuffix(
    instancesCount,
    'Instance',
  )} in Version ${version}${tenant ? ` – ${tenant}` : ''}`;
}

export {getAccordionItemLabel};
