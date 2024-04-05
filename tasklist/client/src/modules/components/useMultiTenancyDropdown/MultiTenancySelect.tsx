/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {Select, SelectItem} from '@carbon/react';
import {useCurrentUser} from 'modules/queries/useCurrentUser';

type Props = React.ComponentProps<typeof Select>;

const MultiTenancySelect: React.FC<Props> = (props) => {
  const {data: currentUser} = useCurrentUser();
  const defaultTenant = currentUser?.tenants[0];
  const tenants = currentUser?.tenants ?? [];

  return (
    <Select
      {...props}
      defaultValue={defaultTenant}
      disabled={props.disabled || currentUser === undefined}
    >
      {tenants.map(({id, name}) => (
        <SelectItem key={id} value={id} text={name} />
      ))}
    </Select>
  );
};

export {MultiTenancySelect};
