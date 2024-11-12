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
import {DangerButton} from '../../OperationItem/DangerButton';

describe('Delete Item', () => {
  it('should show the correct icon based on the type', () => {
    render(
      <OperationItems>
        <DangerButton
          type="DELETE"
          onClick={noop}
          title="delete process instance"
        />
      </OperationItems>,
    );

    expect(screen.getByTestId('delete-operation')).toBeInTheDocument();
  });

  it('should render delete button', () => {
    const BUTTON_TITLE = 'Delete Instance 1';
    render(
      <OperationItems>
        <DangerButton type="DELETE" onClick={noop} title={BUTTON_TITLE} />
      </OperationItems>,
    );

    expect(screen.getByRole('button', {name: /delete/i})).toBeInTheDocument();
  });

  it('should execute callback function', async () => {
    const BUTTON_TITLE = 'Delete Instance 1';
    const MOCK_ON_CLICK = jest.fn();
    const {user} = render(
      <OperationItems>
        <DangerButton
          type="DELETE"
          onClick={MOCK_ON_CLICK}
          title={BUTTON_TITLE}
        />
      </OperationItems>,
    );

    await user.click(screen.getByRole('button', {name: /delete/i}));

    expect(MOCK_ON_CLICK).toHaveBeenCalled();
  });
});
