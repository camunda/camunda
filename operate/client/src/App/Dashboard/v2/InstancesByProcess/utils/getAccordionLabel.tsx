/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import pluralSuffix from 'modules/utils/pluralSuffix';

function getAccordionLabel({
  name,
  instancesCount,
  versionsCount,
  tenant,
}: {
  name: string;
  instancesCount: number;
  versionsCount: number;
  tenant?: string;
}) {
  return `${name} – ${pluralSuffix(
    instancesCount,
    'Instance',
  )} in ${pluralSuffix(versionsCount, 'Version')}${
    tenant ? ` – ${tenant}` : ''
  }`;
}

export {getAccordionLabel};
