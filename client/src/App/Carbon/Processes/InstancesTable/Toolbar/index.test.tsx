/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {render, screen} from 'modules/testing-library';
import {Toolbar} from '.';

describe('<ProcessOperations />', () => {
  it('should not display toolbar if selected instances count is 0 ', async () => {
    render(<Toolbar selectedInstancesCount={0} />);

    expect(screen.queryByText(/items selected/i)).not.toBeInTheDocument();
    expect(
      screen.queryByRole('button', {name: 'Retry'}),
    ).not.toBeInTheDocument();
    expect(
      screen.queryByRole('button', {name: 'Cancel'}),
    ).not.toBeInTheDocument();
    expect(
      screen.queryByRole('button', {name: 'Discard'}),
    ).not.toBeInTheDocument();
  });

  it('should display toolbar with action buttons', async () => {
    const {rerender} = render(<Toolbar selectedInstancesCount={1} />);

    expect(screen.getAllByRole('button', {name: 'Cancel'}).length).toBe(2);
    expect(screen.getByRole('button', {name: 'Retry'}));
    expect(screen.getByRole('button', {name: 'Discard'}));
    expect(screen.getByText('1 item selected'));

    rerender(<Toolbar selectedInstancesCount={10} />);

    expect(screen.getByText('10 items selected'));
  });
});
