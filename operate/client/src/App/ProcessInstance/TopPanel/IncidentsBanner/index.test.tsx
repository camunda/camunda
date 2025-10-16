/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {IncidentsBanner} from './index';
import {render, screen} from 'modules/testing-library';

const getComponentProps = (
  incidentsCount = 1,
): React.ComponentProps<typeof IncidentsBanner> => ({
  onClick: vi.fn(),
  incidentsCount,
  processInstanceKey: '1',
  isOpen: false,
});

describe('IncidentsBanner', () => {
  it('should display incidents banner if banner is not collapsed', async () => {
    const props = getComponentProps();
    render(<IncidentsBanner {...props} />);

    expect(screen.getByText('1 Incident occurred')).toBeInTheDocument();
  });

  it('should show the right text for more than 1 incident', async () => {
    const props = getComponentProps(2);

    render(<IncidentsBanner {...props} />);

    expect(screen.getByText('2 Incidents occurred')).toBeInTheDocument();
  });
});
