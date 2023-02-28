/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React from 'react';
import {MultiRow} from './index';
import {render, screen} from 'modules/testing-library';

const MOCK_CONTENT = 'Row';
const DummyComponent: React.FC = () => <div>{MOCK_CONTENT}</div>;

describe('MultiRow', () => {
  it('should render no rows', () => {
    render(<MultiRow Component={DummyComponent} rowsToDisplay={0} />);

    expect(screen.queryByText(MOCK_CONTENT)).not.toBeInTheDocument();
  });

  it('should render 5 rows with child', () => {
    const NUMBER_OF_ROWS = 5;
    render(
      <MultiRow Component={DummyComponent} rowsToDisplay={NUMBER_OF_ROWS} />
    );

    expect(screen.queryAllByText(MOCK_CONTENT)).toHaveLength(NUMBER_OF_ROWS);
  });
});
