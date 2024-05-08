/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {render, screen} from 'modules/testing-library';
import noop from 'lodash/noop';
import {OperationItems} from '../';
import {OperationItem} from '../../OperationItem';

describe('Retry Item', () => {
  it('should show the correct icon based on the type', () => {
    render(
      <OperationItems>
        <OperationItem
          type="RESOLVE_INCIDENT"
          onClick={noop}
          title="resolve incident"
        />
      </OperationItems>,
    );

    expect(screen.getByTestId('retry-operation')).toBeInTheDocument();
  });

  it('should render retry button', () => {
    const BUTTON_TITLE = 'Retry Instance 1';
    render(
      <OperationItems>
        <OperationItem
          type="RESOLVE_INCIDENT"
          onClick={noop}
          title={BUTTON_TITLE}
        />
      </OperationItems>,
    );

    expect(
      screen.getByRole('button', {name: BUTTON_TITLE}),
    ).toBeInTheDocument();
  });

  it('should execute callback function', async () => {
    const BUTTON_TITLE = 'Retry Instance 1';
    const MOCK_ON_CLICK = jest.fn();
    const {user} = render(
      <OperationItems>
        <OperationItem
          type="RESOLVE_INCIDENT"
          onClick={MOCK_ON_CLICK}
          title={BUTTON_TITLE}
        />
      </OperationItems>,
    );

    await user.click(screen.getByRole('button', {name: BUTTON_TITLE}));

    expect(MOCK_ON_CLICK).toHaveBeenCalled();
  });
});
