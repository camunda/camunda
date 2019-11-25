/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {shallow} from 'enzyme';

import {loadProcess} from './service';
import ProcessWithErrorHandling from './Process';

const Process = ProcessWithErrorHandling.WrappedComponent;

jest.mock('./service', () => ({
  loadProcess: jest.fn().mockReturnValue({
    id: 'processId',
    name: 'Process Name',
    xml: 'Process XML'
  })
}));

const props = {
  match: {params: {id: '1', viewMode: ''}},
  mightFail: jest.fn().mockImplementation((data, cb) => cb(data))
};

it('should load process by id', () => {
  shallow(<Process {...props} />);

  expect(loadProcess).toHaveBeenCalledWith('1');
});

it('should initalize a new process', () => {
  loadProcess.mockClear();
  shallow(<Process {...props} match={{params: {id: 'new'}}} />);

  expect(loadProcess).not.toHaveBeenCalled();
});
