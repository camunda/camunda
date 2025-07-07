/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {render, screen} from 'modules/testing-library';
import {flowNodeTimeStampStore} from 'modules/stores/flowNodeTimeStamp';
import {TimeStampLabel} from './index';
import {MOCK_TIMESTAMP} from 'modules/utils/date/__mocks__/formatDate';
import {act} from 'react';

describe('TimeStampLabel', () => {
  it('should hide/display time stamp on time stamp toggle', async () => {
    render(<TimeStampLabel timeStamp={MOCK_TIMESTAMP} />);

    expect(screen.queryByText(MOCK_TIMESTAMP)).not.toBeInTheDocument();

    act(() => {
      flowNodeTimeStampStore.toggleTimeStampVisibility();
    });

    expect(await screen.findByText(MOCK_TIMESTAMP)).toBeInTheDocument();

    act(() => {
      flowNodeTimeStampStore.toggleTimeStampVisibility();
    });

    expect(screen.queryByText(MOCK_TIMESTAMP)).not.toBeInTheDocument();
  });
});
