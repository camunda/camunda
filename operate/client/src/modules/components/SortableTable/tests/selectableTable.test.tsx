/*
 * Copyright Camunda Services GmbH
 *
 * BY INSTALLING, DOWNLOADING, ACCESSING, USING, OR DISTRIBUTING THE SOFTWARE ("USE"), YOU INDICATE YOUR ACCEPTANCE TO AND ARE ENTERING INTO A CONTRACT WITH, THE LICENSOR ON THE TERMS SET OUT IN THIS AGREEMENT. IF YOU DO NOT AGREE TO THESE TERMS, YOU MUST NOT USE THE SOFTWARE. IF YOU ARE RECEIVING THE SOFTWARE ON BEHALF OF A LEGAL ENTITY, YOU REPRESENT AND WARRANT THAT YOU HAVE THE ACTUAL AUTHORITY TO AGREE TO THE TERMS AND CONDITIONS OF THIS AGREEMENT ON BEHALF OF SUCH ENTITY.
 * "Licensee" means you, an individual, or the entity on whose behalf you receive the Software.
 *
 * Permission is hereby granted, free of charge, to the Licensee obtaining a copy of this Software and associated documentation files to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject in each case to the following conditions:
 * Condition 1: If the Licensee distributes the Software or any derivative works of the Software, the Licensee must attach this Agreement.
 * Condition 2: Without limiting other conditions in this Agreement, the grant of rights is solely for non-production use as defined below.
 * "Non-production use" means any use of the Software that is not directly related to creating products, services, or systems that generate revenue or other direct or indirect economic benefits.  Examples of permitted non-production use include personal use, educational use, research, and development. Examples of prohibited production use include, without limitation, use for commercial, for-profit, or publicly accessible systems or use for commercial or revenue-generating purposes.
 *
 * If the Licensee is in breach of the Conditions, this Agreement, including the rights granted under it, will automatically terminate with immediate effect.
 *
 * SUBJECT AS SET OUT BELOW, THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE, AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 * NOTHING IN THIS AGREEMENT EXCLUDES OR RESTRICTS A PARTY’S LIABILITY FOR (A) DEATH OR PERSONAL INJURY CAUSED BY THAT PARTY’S NEGLIGENCE, (B) FRAUD, OR (C) ANY OTHER LIABILITY TO THE EXTENT THAT IT CANNOT BE LAWFULLY EXCLUDED OR RESTRICTED.
 */

import {render, screen} from 'modules/testing-library';
import {SortableTable} from '../';
import {mockProps, mockSelectableProps, Wrapper} from './mocks';

describe('selectable table', () => {
  it('loading state', () => {
    render(
      <SortableTable {...mockProps} {...mockSelectableProps} state="loading" />,
      {
        wrapper: Wrapper,
      },
    );
    expect(screen.getByTestId('data-table-loader')).toBeInTheDocument();
    expect(screen.getByText('Column Header 1')).toBeInTheDocument();
    expect(screen.getByText('cell content 1')).toBeInTheDocument();

    expect(screen.queryByTestId('data-table-skeleton')).not.toBeInTheDocument();
    expect(screen.queryByText('List is empty')).not.toBeInTheDocument();
    expect(
      screen.queryByText('Data could not be fetched'),
    ).not.toBeInTheDocument();
  });

  it('empty state', () => {
    render(
      <SortableTable
        {...mockProps}
        {...mockSelectableProps}
        state="empty"
        rows={[]}
      />,
      {
        wrapper: Wrapper,
      },
    );
    expect(screen.queryByTestId('data-table-loader')).not.toBeInTheDocument();
    expect(screen.queryByText('Column Header 1')).not.toBeInTheDocument();
    expect(screen.queryByText('cell content 1')).not.toBeInTheDocument();

    expect(screen.queryByTestId('data-table-skeleton')).not.toBeInTheDocument();
    expect(screen.getByText('List is empty')).toBeInTheDocument();
    expect(
      screen.queryByText('Data could not be fetched'),
    ).not.toBeInTheDocument();
    expect(
      screen.queryByRole('checkbox', {name: 'Select all rows'}),
    ).not.toBeInTheDocument();
  });

  it('error state', () => {
    render(
      <SortableTable
        {...mockProps}
        {...mockSelectableProps}
        state="error"
        rows={[]}
      />,
      {
        wrapper: Wrapper,
      },
    );
    expect(screen.queryByTestId('data-table-loader')).not.toBeInTheDocument();
    expect(screen.queryByText('Column Header 1')).not.toBeInTheDocument();
    expect(screen.queryByText('cell content 1')).not.toBeInTheDocument();

    expect(screen.queryByTestId('data-table-skeleton')).not.toBeInTheDocument();
    expect(screen.queryByText('List is empty')).not.toBeInTheDocument();
    expect(screen.getByText('Data could not be fetched')).toBeInTheDocument();
    expect(
      screen.queryByRole('checkbox', {name: 'Select all rows'}),
    ).not.toBeInTheDocument();
  });

  it('skeleton state', () => {
    render(
      <SortableTable
        {...mockProps}
        {...mockSelectableProps}
        state="skeleton"
        rows={[]}
      />,
      {
        wrapper: Wrapper,
      },
    );
    expect(screen.queryByTestId('data-table-loader')).not.toBeInTheDocument();
    expect(screen.getByText('Column Header 1')).toBeInTheDocument();
    expect(screen.queryByText('cell content 1')).not.toBeInTheDocument();
    expect(screen.getByTestId('data-table-skeleton')).toBeInTheDocument();
    expect(screen.queryByText('List is empty')).not.toBeInTheDocument();
    expect(
      screen.queryByText('Data could not be fetched'),
    ).not.toBeInTheDocument();
    expect(
      screen.queryByRole('checkbox', {name: 'Select all rows'}),
    ).not.toBeInTheDocument();
  });

  it('content state', () => {
    render(
      <SortableTable {...mockProps} {...mockSelectableProps} state="content" />,
      {
        wrapper: Wrapper,
      },
    );

    expect(screen.queryByTestId('data-table-loader')).not.toBeInTheDocument();
    expect(screen.getByText('Column Header 1')).toBeInTheDocument();
    expect(screen.getByText('cell content 1')).toBeInTheDocument();
    expect(screen.queryByTestId('data-table-skeleton')).not.toBeInTheDocument();
    expect(screen.queryByTestId('data-table-loader')).not.toBeInTheDocument();
    expect(screen.queryByText('List is empty')).not.toBeInTheDocument();
    expect(
      screen.queryByText('Data could not be fetched'),
    ).not.toBeInTheDocument();
    expect(
      screen.getByRole('checkbox', {name: 'Select all rows'}),
    ).toBeEnabled();
    expect(screen.getAllByRole('checkbox', {name: /Select row/})).toHaveLength(
      4,
    );
  });
});
