/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {render, screen} from 'modules/testing-library';
import {BatchModificationFooter} from '..';
import {tracking} from 'modules/tracking';

jest.mock('modules/hooks/useCallbackPrompt', () => {
  return {
    useCallbackPrompt: () => ({
      shouldInterrupt: false,
      confirmNavigation: jest.fn(),
      cancelNavigation: jest.fn(),
    }),
  };
});

describe('BatchModificationFooter - tracking', () => {
  const trackSpy = jest.spyOn(tracking, 'track');

  it('should track exit click', async () => {
    const {user} = render(<BatchModificationFooter />);

    await user.click(screen.getByRole('button', {name: /exit/i}));

    expect(trackSpy).toHaveBeenCalledWith({
      eventName: 'batch-move-modification-exit-button-clicked',
    });
  });
});
