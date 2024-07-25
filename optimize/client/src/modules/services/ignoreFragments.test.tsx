/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import ignoreFragments from './ignoreFragments';

it('should remove all fragments from children', () => {
  const result = ignoreFragments(
    <>
      <option value="1">first</option>
      <>
        <option value="2">second</option>
        <option value="3">third</option>
      </>
    </>
  );

  expect(result.length).toBe(3);
  const values = result?.map((child) => child?.props.value);
  expect(values).toEqual(['1', '2', '3']);
});
