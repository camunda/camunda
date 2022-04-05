/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import pluralSuffix from 'modules/utils/pluralSuffix';

export function concatGroupTitle(
  processName: any,
  instancesCount: any,
  versionsCount: any
) {
  return `View ${pluralSuffix(instancesCount, 'Instance')} in ${pluralSuffix(
    versionsCount,
    'Version'
  )} of Process ${processName}`;
}

export function concatTitle(
  processName: any,
  instancesCount: any,
  versionName: any
) {
  return `View ${pluralSuffix(
    instancesCount,
    'Instance'
  )} in Version ${versionName} of Process ${processName}`;
}

export function concatGroupLabel(
  name: any,
  instancesCount: any,
  versionsCount: any
) {
  return `${name} – ${pluralSuffix(
    instancesCount,
    'Instance'
  )} in ${pluralSuffix(versionsCount, 'Version')}`;
}

export function concatLabel(name: any, instancesCount: any, version: any) {
  return `${name} – ${pluralSuffix(
    instancesCount,
    'Instance'
  )} in Version ${version}`;
}

export function concatButtonTitle(name: any, instancesCount: any) {
  return `Expand ${pluralSuffix(
    instancesCount,
    'Instance'
  )} of Process ${name}`;
}
