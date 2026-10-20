/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {render, screen} from 'modules/testing-library';
import {StructuredValue} from './index';

describe('<StructuredValue />', () => {
  it('should give nested objects and lists the full field width without changing their values', () => {
    render(
      <StructuredValue
        value={{
          matchCount: 2,
          matches: [
            {id: 'TX-0911', date: '2026-09-14', amount: 482.17},
            {id: 'TX-0912', date: '2026-09-15', amount: 482.17},
          ],
          summary: {duplicateConfirmed: true},
        }}
      />,
    );

    for (const name of ['Matches', 'Summary']) {
      const label = screen.getByText(name);
      expect(label).toHaveStyle('grid-column: 1/-1');
      expect(label.nextElementSibling).toHaveStyle('grid-column: 1/-1');
    }
    expect(screen.getByText('Match count')).not.toHaveStyle(
      'grid-column: 1/-1',
    );
    expect(screen.getByText('TX-0911')).toBeInTheDocument();
    expect(screen.getByText('2026-09-15')).toBeInTheDocument();
    expect(screen.getByText('Yes')).toBeInTheDocument();
  });
});
