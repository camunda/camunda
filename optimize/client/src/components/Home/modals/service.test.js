/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {UNAUTHORIZED_TENANT_ID} from 'services';

import {formatTenants} from './service';

it('should correctly format tenants', () => {
  const tenants = [
    {id: null, name: 'Not defined'},
    {id: 'tenant_id', name: null},
    {id: UNAUTHORIZED_TENANT_ID, name: 'Unauthorized Tenant'},
  ];

  const selectedTenants = [tenants[0]];

  expect(formatTenants(tenants, selectedTenants)).toMatchSnapshot();
});
