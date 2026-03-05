/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {render, screen} from 'modules/testing-library.ts';
import {EmbeddedFormWarningNotification} from './index.tsx';

describe('EmbeddedFormWarningNotification', () => {
  it('should render warning notification with correct content', () => {
    render(<EmbeddedFormWarningNotification />);

    expect(
      screen.getByText(
        'Embedded forms in the source user tasks will be replaced by the form defined in the target element.',
      ),
    ).toBeInTheDocument();
  });

  it('should render link to documentation', () => {
    render(<EmbeddedFormWarningNotification />);

    const link = screen.getByRole('link', {
      name: 'Learn more about migration of user tasks with embedded forms',
    });
    expect(link).toBeInTheDocument();
    expect(link).toHaveAttribute(
      'href',
      'https://docs.camunda.io/docs/components/concepts/process-instance-migration/#migrate-job-worker-user-tasks-to-camunda-user-tasks',
    );
    expect(link).toHaveAttribute('target', '_blank');
  });
});
