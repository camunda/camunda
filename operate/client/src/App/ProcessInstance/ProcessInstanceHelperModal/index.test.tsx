/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {render, screen} from 'modules/testing-library';
import {ProcessInstanceHelperModal} from '.';
import {getStateLocally} from 'modules/utils/localStorage';

describe('ProcessInstanceHelperModal', () => {
  beforeEach(() => {
    localStorage.clear();
  });

  afterEach(() => {
    localStorage.clear();
  });

  it('should render modal with "Learn more" and "Dismiss" buttons', () => {
    render(<ProcessInstanceHelperModal open={true} onClose={vi.fn()} />);

    expect(
      screen.getByText('New Process Instance Details'),
    ).toBeInTheDocument();
    expect(
      screen.getByRole('button', {name: /learn more/i}),
    ).toBeInTheDocument();
    expect(screen.getByRole('button', {name: /dismiss/i})).toBeInTheDocument();
  });

  it('should call onClose when "Dismiss" is clicked', async () => {
    const onCloseMock = vi.fn();

    const {user} = render(
      <ProcessInstanceHelperModal open={true} onClose={onCloseMock} />,
    );

    await user.click(screen.getByRole('button', {name: /dismiss/i}));
    expect(onCloseMock).toHaveBeenCalledTimes(1);
  });

  it('should open blog link and call onClose when "Learn more" is clicked', async () => {
    const onCloseMock = vi.fn();
    const windowOpenMock = vi.spyOn(window, 'open').mockImplementation(vi.fn());

    const {user} = render(
      <ProcessInstanceHelperModal open={true} onClose={onCloseMock} />,
    );

    await user.click(screen.getByRole('button', {name: /learn more/i}));

    expect(windowOpenMock).toHaveBeenCalledWith(
      'https://camunda.com/blog/tag/camunda-platform-8/',
      '_blank',
      'noopener,noreferrer',
    );
    expect(onCloseMock).toHaveBeenCalledTimes(1);

    windowOpenMock.mockRestore();
  });

  it('should set localStorage key when checkbox is checked', async () => {
    const {user} = render(
      <ProcessInstanceHelperModal open={true} onClose={vi.fn()} />,
    );

    expect(getStateLocally()['hideProcessInstanceHelperModal']).toBe(undefined);

    await user.click(screen.getByRole('checkbox'));
    expect(getStateLocally()['hideProcessInstanceHelperModal']).toBe(true);

    await user.click(screen.getByRole('checkbox'));
    expect(getStateLocally()['hideProcessInstanceHelperModal']).toBe(false);
  });
});
