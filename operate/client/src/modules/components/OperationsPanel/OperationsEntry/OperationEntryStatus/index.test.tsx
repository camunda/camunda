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

import {render, screen} from '@testing-library/react';
import OperationsEntryStatus from './index';

describe('OperationsEntryStatus', () => {
  it('should render instance status count when there is one instance with success status', () => {
    render(
      <OperationsEntryStatus
        isTypeDeleteProcessOrDecision={false}
        label={'Edit'}
        failedOperationsCount={0}
        completedOperationsCount={1}
      />,
    );

    expect(screen.getByText('1 success')).toBeInTheDocument();
    expect(screen.queryByText(/\d+\s(fail)/)).not.toBeInTheDocument();
  });

  it('should render instance status count when there is one instance with fail status', () => {
    render(
      <OperationsEntryStatus
        isTypeDeleteProcessOrDecision={false}
        label={'Edit'}
        failedOperationsCount={1}
        completedOperationsCount={0}
      />,
    );

    expect(screen.getByText('1 fail')).toBeInTheDocument();
    expect(screen.queryByText(/\d+\s(success)/)).not.toBeInTheDocument();
  });

  it('should render only success instance status count when all operations have been successful', () => {
    render(
      <OperationsEntryStatus
        isTypeDeleteProcessOrDecision={false}
        label={'Edit'}
        failedOperationsCount={0}
        completedOperationsCount={3}
      />,
    );

    expect(screen.getByText('3 success')).toBeInTheDocument();
    expect(screen.queryByText(/\d+\s(fail)/)).not.toBeInTheDocument();
  });

  it('should render only failed instance status count when all operations have failed', () => {
    render(
      <OperationsEntryStatus
        isTypeDeleteProcessOrDecision={false}
        label={'Edit'}
        failedOperationsCount={3}
        completedOperationsCount={0}
      />,
    );

    expect(screen.getByText('3 fail')).toBeInTheDocument();
    expect(screen.queryByText(/\d+\s(success)/)).not.toBeInTheDocument();
  });

  it('should render success and fail instance status count when there is a mix of failed and successful operations', () => {
    render(
      <OperationsEntryStatus
        isTypeDeleteProcessOrDecision={false}
        label={'Edit'}
        failedOperationsCount={3}
        completedOperationsCount={3}
      />,
    );

    expect(screen.getByText('3 success')).toBeInTheDocument();
    expect(screen.getByText('3 fail')).toBeInTheDocument();
  });

  it('should render delete process or decision definition instance status count when all operations have been successful', () => {
    render(
      <OperationsEntryStatus
        isTypeDeleteProcessOrDecision={true}
        label={'Delete'}
        failedOperationsCount={0}
        completedOperationsCount={3}
      />,
    );

    expect(screen.getByText('3 instances deleted')).toBeInTheDocument();
    expect(screen.queryByText(/\d+\s(fail)/)).not.toBeInTheDocument();
  });

  it('should render delete process or decision definition instance status count when all operations have failed', () => {
    render(
      <OperationsEntryStatus
        isTypeDeleteProcessOrDecision={true}
        label={'Delete'}
        failedOperationsCount={3}
        completedOperationsCount={0}
      />,
    );

    expect(screen.getByText('3 fail')).toBeInTheDocument();
    expect(
      screen.queryByText(/\d+\s(instance(s)? deleted)/),
    ).not.toBeInTheDocument();
  });

  it('should render delete process or decision definition instance status count when there is a mix of failed and successful operations', () => {
    render(
      <OperationsEntryStatus
        isTypeDeleteProcessOrDecision={true}
        label={'Delete'}
        failedOperationsCount={3}
        completedOperationsCount={3}
      />,
    );

    expect(screen.getByText('3 instances deleted')).toBeInTheDocument();
    expect(screen.getByText('3 fail')).toBeInTheDocument();
  });

  it('should not render instances status count for delete instance operation success', () => {
    render(
      <OperationsEntryStatus
        isTypeDeleteProcessOrDecision={false}
        label={'Delete'}
        failedOperationsCount={0}
        completedOperationsCount={1}
      />,
    );

    expect(
      screen.queryByText(/\d+\s(instance(s)? deleted)/),
    ).not.toBeInTheDocument();
    expect(screen.queryByText(/\d+\s(fail)/)).not.toBeInTheDocument();
    expect(screen.queryByText(/\d+\s(success)/)).not.toBeInTheDocument();
  });

  it('should render instances status count for delete instance operation fail', () => {
    render(
      <OperationsEntryStatus
        isTypeDeleteProcessOrDecision={false}
        label={'Delete'}
        failedOperationsCount={1}
        completedOperationsCount={0}
      />,
    );

    expect(screen.getByText('1 fail')).toBeInTheDocument();
    expect(
      screen.queryByText(/\d+\s(instance(s)? deleted)/),
    ).not.toBeInTheDocument();
    expect(screen.queryByText(/\d+\s(success)/)).not.toBeInTheDocument();
  });
});
