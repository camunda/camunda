/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {render, screen} from 'common/testing/testing-library';
import {PriorityLabel} from './index';

describe('PriorityLabel', () => {
  const priorities = [
    {priority: 20, short: 'Low', long: 'Priority: Low'},
    {priority: 50, short: 'Medium', long: 'Priority: Medium'},
    {priority: 60, short: 'High', long: 'Priority: High'},
    {priority: 80, short: 'Critical', long: 'Priority: Critical'},
  ];

  priorities.forEach(({priority, short, long}) => {
    it(`displays the correct short label "${short}" for priority ${priority}`, () => {
      render(<PriorityLabel priority={priority} />);

      expect(screen.getByText(short)).toBeInTheDocument();
    });

    it(`displays the correct popover content for ${long} priority`, () => {
      render(<PriorityLabel priority={priority} />);

      expect(screen.getByTitle(long)).toBeInTheDocument();
      expect(screen.getByText(long)).toBeInTheDocument();
    });
  });
});
