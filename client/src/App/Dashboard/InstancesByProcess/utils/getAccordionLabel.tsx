/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import pluralSuffix from 'modules/utils/pluralSuffix';

function getAccordionLabel(
  name: string,
  instancesCount: number,
  versionsCount: number
) {
  return `${name} – ${pluralSuffix(
    instancesCount,
    'Instance'
  )} in ${pluralSuffix(versionsCount, 'Version')}`;
}

export {getAccordionLabel};
