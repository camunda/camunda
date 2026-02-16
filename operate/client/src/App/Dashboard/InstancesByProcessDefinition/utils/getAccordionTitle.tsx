/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import pluralSuffix from 'modules/utils/pluralSuffix';

function getAccordionTitle({
  processName,
  instancesCount,
  hasMultipleVersions,
  tenant,
}: {
  processName: string;
  instancesCount: number;
  hasMultipleVersions: boolean;
  tenant?: string;
}) {
  const versionsText = hasMultipleVersions ? '2+ Versions' : '1 Version';

  return `View ${pluralSuffix(instancesCount, 'Instance')} in ${versionsText} of Process ${processName}${tenant ? ` – ${tenant}` : ''}`;
}

export {getAccordionTitle};
