/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {VariablesTab} from '../index';
import {render, screen, within} from 'modules/testing-library';
import {createVariable, searchResult} from 'modules/testUtils';
import {mockFetchProcessDefinitionXml} from 'modules/mocks/api/v2/processDefinitions/fetchProcessDefinitionXml';
import {mockFetchProcessInstance} from 'modules/mocks/api/v2/processInstances/fetchProcessInstance';
import {mockSearchVariables} from 'modules/mocks/api/v2/variables/searchVariables';
import {getWrapper, mockProcessInstance} from './mocks';

const createRedactedVariable = (name: string, value: string) => ({
  ...createVariable({name, value, isTruncated: true}),
  metadata: {protectionModes: ['REDACT']},
});

describe('VariablesTab redacted variables', () => {
  beforeEach(() => {
    mockFetchProcessInstance().withSuccess(mockProcessInstance);
    mockFetchProcessDefinitionXml().withSuccess('');
  });

  it('should show [REDACTED] instead of the value of a redacted variable', async () => {
    mockSearchVariables().withSuccess(
      searchResult([
        createVariable({name: 'amount', value: '100'}),
        createRedactedVariable('password', '"irrelevant'),
      ]),
    );

    render(<VariablesTab />, {wrapper: getWrapper()});
    await screen.findByTestId('variables-list');

    const row = within(screen.getByTestId('variable-password'));
    expect(row.getByText('[REDACTED]')).toBeInTheDocument();
    expect(row.queryByText(/irrelevant/)).not.toBeInTheDocument();
    expect(
      row.queryByRole('button', {name: 'Copy password'}),
    ).not.toBeInTheDocument();
    expect(
      row.queryByRole('button', {name: 'Show all'}),
    ).not.toBeInTheDocument();
    expect(
      row.queryByRole('button', {name: 'Edit variable password'}),
    ).not.toBeInTheDocument();
    expect(
      within(screen.getByTestId('variable-amount')).getByText('100'),
    ).toBeInTheDocument();
  });
});
