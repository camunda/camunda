/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {trimVariable} from './';

describe('trimVariable', () => {
  it('should trim white spaces and line breaks', () => {
    expect(
      trimVariable({name: '  myVar  \n  \r  ', value: '   "1" \r  \n  '})
    ).toEqual({
      name: 'myVar',
      value: '"1"'
    });
  });

  it('should not remove white spaces and line breaks fom variable value itself', () => {
    const value = '" \r \n   1, 2   \r \n  "';

    expect(trimVariable({name: 'myVar', value})).toEqual({
      name: 'myVar',
      value
    });
  });

  it('should return variable with empty spaces', () => {
    expect(trimVariable({name: '', value: ''})).toEqual({
      name: '',
      value: ''
    });
  });

  it('should trim and return variable with empty spaces', () => {
    expect(trimVariable({name: '  \r ', value: ' \n '})).toEqual({
      name: '',
      value: ''
    });

    expect(trimVariable({name: '', value: ' \n '})).toEqual({
      name: '',
      value: ''
    });

    expect(trimVariable({name: ' \r ', value: ''})).toEqual({
      name: '',
      value: ''
    });
  });
});
