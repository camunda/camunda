/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {render, screen} from 'modules/testing-library';
import noop from 'lodash/noop';
import {ThemeProvider} from 'modules/theme/ThemeProvider';
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
      {wrapper: ThemeProvider}
    );

    expect(screen.getByTestId('delete-operation')).toBeInTheDocument();
  });

  it('should render delete button', () => {
    const BUTTON_TITLE = 'Delete Instance 1';
    render(
      <OperationItems>
        <DangerButton type="DELETE" onClick={noop} title={BUTTON_TITLE} />
      </OperationItems>,
      {wrapper: ThemeProvider}
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
      {wrapper: ThemeProvider}
    );

    await user.click(screen.getByRole('button', {name: /delete/i}));

    expect(MOCK_ON_CLICK).toHaveBeenCalled();
  });
});
