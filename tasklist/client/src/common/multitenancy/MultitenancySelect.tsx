/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Select, SelectItem} from '@carbon/react';
import {useCurrentUser} from 'common/api/useCurrentUser.query';

type Props = React.ComponentProps<typeof Select>;

const MultitenancySelect: React.FC<Props> = (props) => {
  const {data: currentUser} = useCurrentUser();
  const defaultTenant = currentUser?.tenants[0];
  const tenants = currentUser?.tenants ?? [];

  return (
    <Select
      {...props}
      defaultValue={defaultTenant}
      disabled={props.disabled || currentUser === undefined}
    >
      {tenants.map(({tenantId, name}) => (
        <SelectItem key={tenantId} value={tenantId} text={name} />
      ))}
    </Select>
  );
};

export {MultitenancySelect};
