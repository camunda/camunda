/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
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
