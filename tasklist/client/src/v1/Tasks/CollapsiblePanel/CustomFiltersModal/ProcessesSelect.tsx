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
import {useTranslation} from 'react-i18next';

type Props = {
  tenantId?: string;
} & React.ComponentProps<typeof Select>;

const ProcessesSelect: React.FC<Props> = ({
  tenantId = DEFAULT_TENANT_ID,
  disabled,
  ...props
}) => {
  const {t} = useTranslation();
  const {data} = useProcesses(
    {
      tenantId,
    },
    {
      enabled: !disabled,
      refetchInterval: false,
    },
  );
  const processes = data?.processes ?? [];

  return (
    <Select {...props} disabled={disabled || processes.length === 0}>
      <SelectItem value="all" text={t('customFiltersModalAllProcesses')} />
      {processes.map((process) => (
        <SelectItem
          key={process.id}
          value={process.id}
          text={process.name ?? process.bpmnProcessId}
        />
      ))}
    </Select>
  );
};

export {ProcessesSelect};
