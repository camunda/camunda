/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {render, screen} from 'modules/testing-library';
import {ValidationMessage} from './ValidationMessage';

describe('<ValidationMessage />', async () => {
  describe('when one field is invalid', async () => {
    it('shows field names', async () => {
      render(<ValidationMessage fieldIds={['a']} fieldLabels={['A']} />);

      const alert = screen.getByRole('alert', {
        name: 'Please review 1 field: A',
      });
      expect(alert).toBeInTheDocument();
      expect(alert).toHaveTextContent('Please review 1 field: A');
    });

    it('shows number of invalid fields', async () => {
      render(<ValidationMessage fieldIds={['a']} fieldLabels={[]} />);

      const alert = screen.getByRole('alert', {name: 'Please review 1 field'});
      expect(alert).toBeInTheDocument();
      expect(alert).toHaveTextContent('Please review 1 field');
    });
  });

  describe('when two fields are invalid', async () => {
    it('shows field names', async () => {
      render(
        <ValidationMessage fieldIds={['a', 'b']} fieldLabels={['A', 'B']} />,
      );

      const alert = screen.getByRole('alert', {
        name: 'Please review 2 fields: A, and B',
      });
      expect(alert).toBeInTheDocument();
      expect(alert).toHaveTextContent('Please review 2 fields: A, and B');
    });

    it('shows number of invalid fields', async () => {
      render(<ValidationMessage fieldIds={['a', 'b']} fieldLabels={[]} />);

      const alert = screen.getByRole('alert', {name: 'Please review 2 fields'});
      expect(alert).toBeInTheDocument();
      expect(alert).toHaveTextContent('Please review 2 fields');
    });
  });

  describe('when five fields are invalid', async () => {
    it('shows field names', async () => {
      render(
        <ValidationMessage
          fieldIds={['a', 'b', 'c', 'd', 'e']}
          fieldLabels={['A', 'B', 'C', 'D', 'E']}
        />,
      );

      const alert = screen.getByRole('alert', {
        name: 'Please review 5 fields: A, B, C, D, and E',
      });
      expect(alert).toBeInTheDocument();
      expect(alert).toHaveTextContent(
        'Please review 5 fields: A, B, and 3 more',
      );
    });

    it('shows number of invalid fields', async () => {
      render(
        <ValidationMessage
          fieldIds={['a', 'b', 'c', 'd', 'e']}
          fieldLabels={[]}
        />,
      );

      const alert = screen.getByRole('alert', {name: 'Please review 5 fields'});
      expect(alert).toBeInTheDocument();
      expect(alert).toHaveTextContent('Please review 5 fields');
    });

    it('shows number of invalid fields and all named fields', async () => {
      render(
        <ValidationMessage
          fieldIds={['a', 'b', 'c', 'd', 'e']}
          fieldLabels={['A', 'B']}
        />,
      );

      const alert = screen.getByRole('alert', {
        name: 'Please review 5 fields: A, B, and 3 more',
      });
      expect(alert).toBeInTheDocument();
      expect(alert).toHaveTextContent(
        'Please review 5 fields: A, B, and 3 more',
      );
    });
  });
});
