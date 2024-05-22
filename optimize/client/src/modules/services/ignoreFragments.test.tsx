/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
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
