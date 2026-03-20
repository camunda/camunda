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

  it('should render modal content', () => {
    render(<ProcessInstanceHelperModal open={true} onClose={vi.fn()} />);

    expect(
      screen.getByText("Here's what moved in Operate"),
    ).toBeInTheDocument();
    expect(screen.getByRole('button', {name: /got it/i})).toBeInTheDocument();
    expect(
      screen.getByText(/we made some changes to the process instance page/i),
    ).toBeInTheDocument();
    expect(screen.getByRole('list')).toBeInTheDocument();
    expect(screen.getAllByRole('listitem')).toHaveLength(3);
    expect(
      screen.getByAltText(
        'Process instance details page with incidents tab and diagram',
      ),
    ).toBeInTheDocument();
  });

  it('should call onClose and set localStorage when "Got it" is clicked', async () => {
    const onCloseMock = vi.fn();

    const {user} = render(
      <ProcessInstanceHelperModal open={true} onClose={onCloseMock} />,
    );

    await user.click(screen.getByRole('button', {name: /got it/i}));
    expect(onCloseMock).toHaveBeenCalledTimes(1);
    expect(getStateLocally()['hideProcessInstanceHelperModal']).toBe(true);
  });

  it('should call onClose and set localStorage when close button is clicked', async () => {
    const onCloseMock = vi.fn();

    const {user} = render(
      <ProcessInstanceHelperModal open={true} onClose={onCloseMock} />,
    );

    await user.click(screen.getByRole('button', {name: /close/i}));
    expect(onCloseMock).toHaveBeenCalledTimes(1);
    expect(getStateLocally()['hideProcessInstanceHelperModal']).toBe(true);
  });
});
