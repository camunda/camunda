/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {getValidVariableValues} from './getValidVariableValues';

describe('getValidVariableValues', () => {
  describe('single values', () => {
    const values = [
      {i: '"a"', o: ['a']},
      {i: '1', o: [1]},
      {i: '{"client": "Bob"}', o: [{client: 'Bob'}]},
      {i: '  "test"  ', o: ['test']},
      {i: ',"test",', o: ['test']},
      {i: '"test",,', o: ['test']},
      {i: ',,"test"', o: ['test']},
      {i: 'invalid', o: undefined},
      {i: '+', o: undefined},
      {i: '', o: []},
      {i: ',', o: []},
    ];

    test.each(values)('input: $i', ({i: input, o: output}) => {
      expect(getValidVariableValues(input)).toStrictEqual(output);
    });
  });

  describe('list of values', () => {
    const values = [
      {i: '"a", "b"', o: ['a', 'b']},
      {i: '0, 99', o: [0, 99]},
      {
        i: ',,,"a", [1, "2", 3], 0.1, {"user": "Bob"},,,',
        o: ['a', [1, '2', 3], 0.1, {user: 'Bob'}],
      },
      {i: '+, -, §', o: undefined},
    ];

    test.each(values)('input: $i', ({i: input, o: output}) => {
      expect(getValidVariableValues(input)).toStrictEqual(output);
    });
  });
});
