/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Breadcrumb, BreadcrumbItem} from '@carbon/react';

import {t} from 'translation';

interface DrillBreadcrumbProps {
  // Agent inventory when the agent view was opened from the inventory tab, else all processes.
  rootLabelKey: string;
  processName: string | null;
  agentName: string | null;
  onRoot: () => void;
  onProcess: () => void;
}

export function DrillBreadcrumb({
  rootLabelKey,
  processName,
  agentName,
  onRoot,
  onProcess,
}: DrillBreadcrumbProps) {
  if (!processName && !agentName) {
    return null;
  }

  return (
    <Breadcrumb className="DrillBreadcrumb" noTrailingSlash>
      <BreadcrumbItem>
        <button type="button" className="crumb" onClick={onRoot}>
          {t(rootLabelKey)}
        </button>
      </BreadcrumbItem>
      {processName && (
        <BreadcrumbItem isCurrentPage={!agentName}>
          {agentName ? (
            <button type="button" className="crumb" onClick={onProcess}>
              {processName}
            </button>
          ) : (
            processName
          )}
        </BreadcrumbItem>
      )}
      {agentName && <BreadcrumbItem isCurrentPage>{agentName}</BreadcrumbItem>}
    </Breadcrumb>
  );
}
