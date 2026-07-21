/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {render} from 'modules/testing-library';
import {InstanceHistoryTab} from './index';
import {ElementInstanceLog} from 'App/ProcessInstance/ElementInstanceLog';

vi.mock('App/ProcessInstance/ElementInstanceLog', () => ({
  ElementInstanceLog: vi.fn(() => null),
}));

describe('InstanceHistoryTab', () => {
  it('renders ElementInstanceLog as a panel with the header hidden', () => {
    render(<InstanceHistoryTab />);

    expect(ElementInstanceLog).toHaveBeenCalledWith(
      {isPanel: true, showHeader: false},
      {},
    );
  });
});
