/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {render, screen} from 'modules/testing-library';
import {pickDateRange} from 'modules/testUtils/pickDateRange';
import {ThemeProvider} from 'modules/theme/ThemeProvider';
import {Form} from 'react-final-form';
import {DateRangeField} from '.';

const getWrapper = (initialValues?: {[key: string]: string}) => {
  const Wrapper: React.FC<{children?: React.ReactNode}> = ({children}) => {
    return (
      <ThemeProvider>
        <Form onSubmit={() => {}} initialValues={initialValues}>
          {() => children}
        </Form>
        <div>Outside element</div>
      </ThemeProvider>
    );
  };

  return Wrapper;
};

describe('Date Range', () => {
  it('should render readonly input field', async () => {
    render(
      <DateRangeField
        label="Start Date Range"
        fromDateKey="startDateAfter"
        toDateKey="startDateBefore"
      />,
      {wrapper: getWrapper()}
    );

    expect(screen.getByLabelText('Start Date Range')).toHaveAttribute(
      'readonly'
    );
  });

  it('should close popover on cancel click', async () => {
    const {user} = render(
      <DateRangeField
        label="Start Date Range"
        fromDateKey="startDateAfter"
        toDateKey="startDateBefore"
      />,
      {wrapper: getWrapper()}
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
        label="Start Date Range"
        fromDateKey="startDateAfter"
        toDateKey="startDateBefore"
      />,
      {wrapper: getWrapper()}
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
        label="Start Date Range"
        fromDateKey="startDateAfter"
        toDateKey="startDateBefore"
      />,
      {wrapper: getWrapper()}
    );

    expect(screen.queryByTestId('popover')).not.toBeInTheDocument();

    await user.click(screen.getByLabelText('Start Date Range'));
    await user.click(screen.getByTestId('popover'));
    expect(screen.getByTestId('popover')).toBeInTheDocument();
  });

  it('should pick from and to dates', async () => {
    const {user} = render(
      <DateRangeField
        label="Start Date Range"
        fromDateKey="startDateAfter"
        toDateKey="startDateBefore"
      />,
      {wrapper: getWrapper()}
    );

    await user.click(screen.getByLabelText('Start Date Range'));

    const {year, month, fromDay, toDay} = await pickDateRange({
      user,
      screen,
      fromDay: '10',
      toDay: '20',
    });

    await user.click(screen.getByText('Apply'));
    expect(screen.getByLabelText('Start Date Range')).toHaveValue(
      `${year}-${month}-${fromDay} - ${year}-${month}-${toDay}`
    );
  });

  it('should restore previous date on cancel', async () => {
    const {user} = render(
      <DateRangeField
        label="Start Date Range"
        fromDateKey="startDateAfter"
        toDateKey="startDateBefore"
      />,
      {wrapper: getWrapper()}
    );

    await user.click(screen.getByLabelText('Start Date Range'));
    expect(screen.getByLabelText('Start Date Range')).toHaveValue('Custom');

    const {year, month, fromDay, toDay} = await pickDateRange({
      user,
      screen,
      fromDay: '10',
      toDay: '20',
    });

    await user.click(screen.getByText('Apply'));

    const expectedValue = `${year}-${month}-${fromDay} - ${year}-${month}-${toDay}`;
    expect(screen.getByLabelText('Start Date Range')).toHaveValue(
      expectedValue
    );

    await user.click(screen.getByLabelText('Start Date Range'));
    expect(screen.getByLabelText('Start Date Range')).toHaveValue('Custom');

    await user.click(screen.getByText('Cancel'));
    expect(screen.getByLabelText('Start Date Range')).toHaveValue(
      expectedValue
    );

    await user.click(screen.getByLabelText('Start Date Range'));
    expect(screen.getByLabelText('Start Date Range')).toHaveValue('Custom');

    await user.click(screen.getByText('Outside element'));
    expect(screen.getByLabelText('Start Date Range')).toHaveValue(
      expectedValue
    );
  });

  it('should set default values', async () => {
    const {user} = render(
      <DateRangeField
        label="Start Date Range"
        fromDateKey="startDateAfter"
        toDateKey="startDateBefore"
      />,
      {
        wrapper: getWrapper({
          startDateAfter: '2021-02-03',
          startDateBefore: '2021-02-06',
        }),
      }
    );

    expect(screen.getByLabelText('Start Date Range')).toHaveValue(
      '2021-02-03 - 2021-02-06'
    );

    await user.click(screen.getByLabelText('Start Date Range'));

    expect(screen.getByLabelText('From')).toHaveValue('2021-02-03');
    expect(screen.getByLabelText('To')).toHaveValue('2021-02-06');
  });

  // will be unskipped when https://github.com/camunda/operate/issues/3650 is done
  it.skip('should apply from and to dates', async () => {
    const {user} = render(
      <DateRangeField
        label="Start Date Range"
        fromDateKey="startDateAfter"
        toDateKey="startDateBefore"
      />,
      {wrapper: getWrapper()}
    );

    await user.click(screen.getByLabelText('Start Date Range'));
    await user.type(screen.getByLabelText('From'), '2022-01-01 12:30');
    await user.type(screen.getByLabelText('To'), '2022-12-01 17:15');
    await user.click(screen.getByText('Apply'));
    expect(screen.getByLabelText('Start Date Range')).toHaveValue(
      '2022-01-01 12:30 - 2022-12-01 17:15'
    );
  });
});
