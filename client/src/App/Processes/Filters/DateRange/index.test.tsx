/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {render, screen} from 'modules/testing-library';
import {Form} from 'react-final-form';
import {DateRange} from '.';

const Wrapper: React.FC<{children?: React.ReactNode}> = ({children}) => {
  return <Form onSubmit={() => {}}>{() => children}</Form>;
};

describe('Date Range', () => {
  it('should render readonly input field', async () => {
    render(
      <DateRange
        label={'Start Date Range'}
        filterKeys={['startDateBefore', 'startDateAfter']}
      />,
      {wrapper: Wrapper}
    );

    expect(screen.getByLabelText('Start Date Range')).toHaveAttribute(
      'readonly'
    );
  });
});
