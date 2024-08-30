/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {filterSameTypeExistingFilters} from './service';

it('should remove the filters of the same type and the same level', () => {
  const existingFilters = [
    {filterLevel: 'instance', type: 'instanceStartDate'},
    {filterLevel: 'view', type: 'includesOpenIncident'},
  ];

  expect(
    filterSameTypeExistingFilters(existingFilters, {
      filterLevel: 'instance',
      type: 'instanceStartDate',
    })
  ).toEqual([{filterLevel: 'view', type: 'includesOpenIncident'}]);

  expect(
    filterSameTypeExistingFilters(existingFilters, {
      filterLevel: 'instance',
      type: 'includesOpenIncident',
    })
  ).toEqual(existingFilters);
});

it('should keep filters of the same type and level if they apply to different definitions', () => {
  const existingFilters = [
    {filterLevel: 'instance', type: 'instanceStartDate', appliedTo: ['definition1']},
    {filterLevel: 'view', type: 'includesOpenIncident', appliedTo: ['definition1']},
  ];

  expect(
    filterSameTypeExistingFilters(existingFilters, {
      filterLevel: 'instance',
      type: 'instanceStartDate',
      appliedTo: ['definition2'],
    })
  ).toEqual(existingFilters);
});
