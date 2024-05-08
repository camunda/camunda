/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import pluralSuffix from 'modules/utils/pluralSuffix';

function getAccordionItemTitle({
  processName,
  instancesCount,
  versionName,
  errorMessage,
  tenant,
}: {
  processName: string;
  instancesCount: number;
  versionName: number;
  errorMessage: string;
  tenant?: string;
}) {
  return `View ${pluralSuffix(
    instancesCount,
    'Instance',
  )} with error ${errorMessage} in version ${versionName} of Process ${processName}${
    tenant ? ` – ${tenant}` : ''
  }`;
}

export {getAccordionItemTitle};
