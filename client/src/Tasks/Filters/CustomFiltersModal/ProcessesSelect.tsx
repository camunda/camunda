/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {Select, SelectItem} from '@carbon/react';
import {DEFAULT_TENANT_ID} from 'modules/constants/multiTenancy';
import {useProcesses} from 'modules/queries/useProcesses';

interface Props<T = HTMLSelectElement> {
  tenantId?: string;
  value: string;
  name: string;
  onBlur: (event?: React.FocusEvent<T>) => void;
  onChange: (event: React.ChangeEvent<T>) => void;
  onFocus: (event?: React.FocusEvent<T>) => void;
  disabled?: boolean;
}

const ProcessesSelect: React.FC<Props> = ({
  name,
  value,
  onChange,
  onBlur,
  onFocus,
  tenantId = DEFAULT_TENANT_ID,
  disabled,
}) => {
  const {data} = useProcesses(
    {
      tenantId,
    },
    {
      enabled: !disabled,
    },
  );
  const processes = data?.processes ?? [];

  return (
    <Select
      id={name}
      labelText="Process"
      disabled={disabled || processes.length === 0}
      value={value}
      onBlur={onBlur}
      onFocus={onFocus}
      onChange={onChange}
    >
      <SelectItem value="all" text="All processes" />
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
