/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Select, SelectItem} from '@carbon/react';
import {DEFAULT_TENANT_ID} from 'common/multitenancy/constants';
import {useProcesses} from 'v1/api/useProcesses.query';
import {useAllProcessDefinitions} from 'v2/api/useAllProcessDefinitions.query';
import {useTranslation} from 'react-i18next';
import {getClientConfig} from 'common/config/getClientConfig';
import type {Process} from 'v1/api/types';
import type {ProcessDefinition} from '@vzeta/camunda-api-zod-schemas/operate';

const useMultiModeProcesses =
  getClientConfig().clientMode === 'v2'
    ? useAllProcessDefinitions
    : useProcesses;

function isV2Process(
  process: Process[] | ProcessDefinition[],
): process is ProcessDefinition[] {
  return process.length > 0 && 'processDefinitionKey' in process[0];
}

function normalizeProcesses(processes: Process[] | ProcessDefinition[]): {
  value: string;
  label: string;
}[] {
  if (processes.length === 0) {
    return [];
  }

  if (isV2Process(processes)) {
    return processes.map(
      ({processDefinitionKey, name, processDefinitionId, version}) => ({
        value: processDefinitionKey.toString(),
        label: `${name ?? processDefinitionId} - v${version}`,
      }),
    );
  }

  return processes.map(({id, name, bpmnProcessId}) => ({
    value: id,
    label: name ?? bpmnProcessId,
  }));
}

function isV2Result(data: unknown): data is {items: ProcessDefinition[]} {
  return (
    getClientConfig().clientMode === 'v2' &&
    data !== null &&
    typeof data === 'object' &&
    'items' in data
  );
}

type Props = {
  tenantId?: string;
} & React.ComponentProps<typeof Select>;

const ProcessesSelect: React.FC<Props> = ({
  tenantId = DEFAULT_TENANT_ID,
  disabled,
  ...props
}) => {
  const {t} = useTranslation();
  const {data} = useMultiModeProcesses(
    {
      tenantId,
    },
    {
      enabled: !disabled,
      refetchInterval: false,
    },
  );

  const processes = normalizeProcesses(
    (isV2Result(data) ? data.items : data?.processes) ?? [],
  );

  return (
    <Select {...props} disabled={disabled || processes.length === 0}>
      <SelectItem value="all" text={t('customFiltersModalAllProcesses')} />
      {processes.map(({value, label}) => (
        <SelectItem key={value} value={value} text={label} />
      ))}
    </Select>
  );
};

export {ProcessesSelect};
