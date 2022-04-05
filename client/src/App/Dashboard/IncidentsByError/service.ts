/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import pluralSuffix from 'modules/utils/pluralSuffix';

export function concatTitle(
  processName: any,
  instancesCount: any,
  versionName: any,
  errorMessage: any
) {
  return `View ${pluralSuffix(
    instancesCount,
    'Instance'
  )} with error ${errorMessage} in version ${versionName} of Process ${processName}`;
}

export function concatLabel(name: any, version: any) {
  return `${name} – Version ${version}`;
}

export function concatGroupTitle(
  instancesWithErrorCount: any,
  errorMessage: any
) {
  return `View ${pluralSuffix(
    instancesWithErrorCount,
    'Instance'
  )} with error ${errorMessage}`;
}

export function concatButtonTitle(
  instancesWithErrorCount: any,
  errorMessage: any
) {
  return `Expand ${pluralSuffix(
    instancesWithErrorCount,
    'Instance'
  )} with error ${errorMessage}`;
}
