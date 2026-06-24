/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Select, SelectItem} from '@carbon/react';
import {DEFAULT_TENANT_ID} from 'modules/multitenancy/constants';
import {useAllProcessDefinitions} from 'modules/api/useAllProcessDefinitions.query';
import {useTranslation} from 'react-i18next';
import type {ProcessDefinition} from '@camunda/camunda-api-zod-schemas/8.10';

function getSelectOptions(processes: ProcessDefinition[]): {
  value: string;
  label: string;
}[] {
  return processes.map(
    ({processDefinitionKey, name, processDefinitionId, version}) => ({
      value: processDefinitionKey.toString(),
      label: `${name ?? processDefinitionId} - v${version}`,
    }),
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
  const {data} = useAllProcessDefinitions(
    {
      tenantId,
    },
    {
      enabled: !disabled,
      refetchInterval: false,
    },
  );
  const processes = getSelectOptions(data?.items ?? []);

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
