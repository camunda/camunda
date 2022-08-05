/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {render, screen} from 'modules/testing-library';
import {ThemeProvider} from 'modules/theme/ThemeProvider';
import {Form} from 'react-final-form';
import {DateRangeField} from '.';

const Wrapper: React.FC<{children?: React.ReactNode}> = ({children}) => {
  return (
    <ThemeProvider>
      <Form onSubmit={() => {}}>{() => children}</Form>
      <div>Outside element</div>
    </ThemeProvider>
  );
};

describe('Date Range', () => {
  it('should render readonly input field', async () => {
    render(
      <DateRangeField
        label={'Start Date Range'}
        filterKeys={['startDateAfter', 'startDateBefore']}
      />,
      {wrapper: Wrapper}
    );

    expect(screen.getByLabelText('Start Date Range')).toHaveAttribute(
      'readonly'
    );
  });

  it('should close popover on cancel click', async () => {
    const {user} = render(
      <DateRangeField
        label={'Start Date Range'}
        filterKeys={['startDateBefore', 'startDateAfter']}
      />,
      {wrapper: Wrapper}
    );

    expect(screen.queryByTestId('popover')).not.toBeInTheDocument();

    await user.click(screen.getByLabelText('Start Date Range'));
    expect(screen.getByTestId('popover')).toBeInTheDocument();

    // getByRole does not work here because the date range popover portal is rendered to document.body
    await user.click(screen.getByText('Cancel'));
    expect(screen.queryByTestId('popover')).not.toBeInTheDocument();
  });

  it('should close popover on outside click', async () => {
    const {user} = render(
      <DateRangeField
        label={'Start Date Range'}
        filterKeys={['startDateBefore', 'startDateAfter']}
      />,
      {wrapper: Wrapper}
    );

    expect(screen.queryByTestId('popover')).not.toBeInTheDocument();

    await user.click(screen.getByLabelText('Start Date Range'));
    expect(screen.getByTestId('popover')).toBeInTheDocument();

    await user.click(screen.getByText('Outside element'));
    expect(screen.queryByTestId('popover')).not.toBeInTheDocument();
  });

  it('should not close popover on inside click', async () => {
    const {user} = render(
      <DateRangeField
        label={'Start Date Range'}
        filterKeys={['startDateBefore', 'startDateAfter']}
      />,
      {wrapper: Wrapper}
    );

    expect(screen.queryByTestId('popover')).not.toBeInTheDocument();

    await user.click(screen.getByLabelText('Start Date Range'));
    await user.click(screen.getByTestId('popover'));
    expect(screen.getByTestId('popover')).toBeInTheDocument();
  });
});
