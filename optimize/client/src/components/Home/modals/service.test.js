/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
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
