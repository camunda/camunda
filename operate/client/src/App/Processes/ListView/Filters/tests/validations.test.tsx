/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {render, screen} from 'modules/testing-library';
import {getWrapper} from './mocks';
import {createUser, mockProcessXML, searchResult} from 'modules/testUtils';
import {Filters} from '../index';
import {ERRORS} from 'modules/validators';
import {mockFetchProcessDefinitionXml} from 'modules/mocks/api/v2/processDefinitions/fetchProcessDefinitionXml';
import {act} from 'react';
import {mockMe} from 'modules/mocks/api/v2/me';
import {mockSearchProcessDefinitions} from 'modules/mocks/api/v2/processDefinitions/searchProcessDefinitions';

describe('Validations', () => {
  beforeEach(async () => {
    mockSearchProcessDefinitions().withSuccess(searchResult([]));
    mockFetchProcessDefinitionXml().withSuccess(mockProcessXML);
    mockMe().withSuccess(createUser());

    vi.useFakeTimers({shouldAdvanceTime: true});
  });

  afterEach(() => {
    vi.clearAllTimers();
    vi.useRealTimers();
  });

  it('should validate process instance keys', async () => {
    const {user} = render(<Filters />, {
      wrapper: getWrapper(),
    });

    expect(screen.getByTestId('search')).toBeEmptyDOMElement();

    await user.click(screen.getByRole('button', {name: 'More Filters'}));
    await user.click(screen.getByText('Process Instance Key(s)'));
    await user.type(screen.getByLabelText(/^process instance key\(s\)$/i), 'a');

    act(() => {
      vi.runOnlyPendingTimers();
    });

    expect(await screen.findByText(ERRORS.ids)).toBeInTheDocument();
    expect(screen.getByTestId('search')).toBeEmptyDOMElement();

    await user.clear(screen.getByLabelText(/^process instance key\(s\)$/i));

    expect(screen.queryByText(ERRORS.ids)).not.toBeInTheDocument();

    await user.type(screen.getByLabelText(/^process instance key\(s\)$/i), '1');

    act(() => {
      vi.runOnlyPendingTimers();
    });

    expect(await screen.findByText(ERRORS.ids)).toBeInTheDocument();

    await user.clear(screen.getByLabelText(/^process instance key\(s\)$/i));

    expect(screen.queryByText(ERRORS.ids)).not.toBeInTheDocument();
  });

  it('should validate Parent Process Instance Key', async () => {
    const {user} = render(<Filters />, {
      wrapper: getWrapper(),
    });
    expect(screen.getByTestId('search')).toBeEmptyDOMElement();

    await user.click(screen.getByRole('button', {name: 'More Filters'}));
    await user.click(screen.getByText('Parent Process Instance Key'));
    await user.type(
      screen.getByLabelText(/^Parent Process Instance Key$/i),
      'a',
    );

    act(() => {
      vi.runOnlyPendingTimers();
    });

    expect(
      await screen.findByText(ERRORS.parentInstanceId),
    ).toBeInTheDocument();
    expect(screen.getByTestId('search')).toBeEmptyDOMElement();

    await user.clear(screen.getByLabelText(/^Parent Process Instance Key$/i));

    expect(screen.queryByText(ERRORS.parentInstanceId)).not.toBeInTheDocument();

    await user.type(
      screen.getByLabelText(/^Parent Process Instance Key$/i),
      '1',
    );

    act(() => {
      vi.runOnlyPendingTimers();
    });

    expect(
      await screen.findByText(ERRORS.parentInstanceId),
    ).toBeInTheDocument();

    await user.clear(screen.getByLabelText(/^Parent Process Instance Key$/i));

    expect(screen.queryByText(ERRORS.parentInstanceId)).not.toBeInTheDocument();

    await user.type(
      screen.getByLabelText(/^Parent Process Instance Key$/i),
      '1111111111111111, 2222222222222222',
    );

    act(() => {
      vi.runOnlyPendingTimers();
    });

    expect(
      await screen.findByText(ERRORS.parentInstanceId),
    ).toBeInTheDocument();

    await user.clear(screen.getByLabelText(/^Parent Process Instance Key$/i));

    expect(screen.queryByText(ERRORS.parentInstanceId)).not.toBeInTheDocument();
  });

  // Variable name/value validation is now handled inside the VariableFilterModal
  // and is tested in VariableFilterModal.test.tsx

  it('should validate operation id', async () => {
    const {user} = render(<Filters />, {
      wrapper: getWrapper(),
    });
    expect(screen.getByTestId('search')).toBeEmptyDOMElement();

    await user.click(screen.getByRole('button', {name: 'More Filters'}));
    await user.click(screen.getByText('Operation Id'));

    await user.type(screen.getByLabelText(/^operation id$/i), 'g');

    act(() => {
      vi.runOnlyPendingTimers();
    });

    expect(await screen.findByText(ERRORS.operationId)).toBeInTheDocument();

    expect(screen.getByTestId('search')).toBeEmptyDOMElement();

    await user.clear(screen.getByLabelText(/^operation id$/i));

    expect(screen.queryByTitle(ERRORS.operationId)).not.toBeInTheDocument();

    await user.type(screen.getByLabelText(/^operation id$/i), 'a');

    act(() => {
      vi.runOnlyPendingTimers();
    });

    expect(await screen.findByText(ERRORS.operationId)).toBeInTheDocument();
  });
});
