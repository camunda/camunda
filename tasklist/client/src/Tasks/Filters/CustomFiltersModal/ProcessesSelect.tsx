/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {Select, SelectItem} from '@carbon/react';
import {DEFAULT_TENANT_ID} from 'modules/constants/multiTenancy';
import {useProcesses} from 'modules/queries/useProcesses';

type Props = {
  tenantId?: string;
} & React.ComponentProps<typeof Select>;

const ProcessesSelect: React.FC<Props> = ({
  tenantId = DEFAULT_TENANT_ID,
  disabled,
  ...props
}) => {
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
