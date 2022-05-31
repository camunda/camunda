/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import pluralSuffix from 'modules/utils/pluralSuffix';

function getAccordionItemTitle(
  processName: string,
  instancesCount: number,
  version: number
) {
  return `View ${pluralSuffix(
    instancesCount,
    'Instance'
  )} in Version ${version} of Process ${processName}`;
}

export {getAccordionItemTitle};
