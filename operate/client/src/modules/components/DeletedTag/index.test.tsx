/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {render, screen} from 'modules/testing-library';
import {DeletedTag} from './index';

describe('<DeletedTag />', () => {
  it('should render the deleted tag', () => {
    render(<DeletedTag />);

    expect(screen.getByTestId('deleted-tag')).toBeInTheDocument();
    expect(screen.getByText('Deleted')).toBeInTheDocument();
  });
});
