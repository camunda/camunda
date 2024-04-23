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

import {render, screen, act} from 'modules/testing-library';
import {modificationsStore} from 'modules/stores/modifications';
import {Operations} from '../index';
import {INSTANCE, Wrapper} from './mocks';
import {Paths} from 'modules/Routes';

describe('Operations - Cancel Operation', () => {
  it('should show cancel confirmation modal', async () => {
    const modalText =
      'About to cancel Instance instance_1. In case there are called instances, these will be canceled too.';

    const {user} = render(
      <Operations
        instance={{
          ...INSTANCE,
          state: 'INCIDENT',
        }}
      />,
      {wrapper: Wrapper},
    );

    await user.click(
      screen.getByRole('button', {name: 'Cancel Instance instance_1'}),
    );

    expect(screen.getByText(modalText)).toBeInTheDocument();
    expect(screen.getByRole('button', {name: 'Apply'})).toBeInTheDocument();
    expect(screen.getByRole('button', {name: 'Cancel'})).toBeInTheDocument();

    await user.click(screen.getByRole('button', {name: 'Cancel'}));

    expect(screen.queryByText(modalText)).not.toBeInTheDocument();
  });

  it('should show modal when trying to cancel called instance', async () => {
    const onOperationMock = jest.fn();

    const modalText =
      /To cancel this instance, the root instance.*needs to be canceled. When the root instance is canceled all the called instances will be canceled automatically./;

    const {user} = render(
      <Operations
        instance={{
          ...INSTANCE,
          state: 'INCIDENT',
          rootInstanceId: '6755399441058622',
        }}
        onOperation={onOperationMock}
      />,
      {wrapper: Wrapper},
    );

    await user.click(
      screen.getByRole('button', {name: 'Cancel Instance instance_1'}),
    );

    expect(screen.getByText(modalText)).toBeInTheDocument();
    expect(
      screen.queryByRole('button', {name: 'Cancel'}),
    ).not.toBeInTheDocument();
    expect(
      screen.queryByRole('button', {name: 'Apply'}),
    ).not.toBeInTheDocument();

    await user.click(screen.getByRole('button', {name: /close/i}));

    expect(screen.queryByText(modalText)).not.toBeInTheDocument();
  });

  it('should redirect to linked parent instance', async () => {
    const rootInstanceId = '6755399441058622';

    const {user} = render(
      <Operations
        instance={{
          ...INSTANCE,
          state: 'INCIDENT',
          rootInstanceId,
        }}
      />,
      {
        wrapper: Wrapper,
      },
    );

    await user.click(
      screen.getByRole('button', {name: 'Cancel Instance instance_1'}),
    );

    await user.click(
      screen.getByRole('link', {
        description: `View root instance ${rootInstanceId}`,
      }),
    );

    expect(screen.getByTestId('pathname').textContent).toBe(
      Paths.processInstance(rootInstanceId),
    );
  });

  it('should display helper modal when clicking modify instance, until user clicks do not show', async () => {
    const {user} = render(
      <Operations
        instance={{...INSTANCE, state: 'INCIDENT'}}
        isInstanceModificationVisible
      />,
      {
        wrapper: Wrapper,
      },
    );

    await user.click(await screen.findByTitle('Modify Instance instance_1'));
    expect(
      screen.queryByTestId('apply-modifications-button'),
    ).not.toBeInTheDocument();

    expect(
      screen.getByText(
        'Process instance modification mode allows you to plan multiple modifications on a process instance.',
      ),
    ).toBeInTheDocument();

    await user.click(
      screen.getByRole('checkbox', {name: 'Do not show this message again'}),
    );
    await user.click(screen.getByRole('button', {name: 'Continue'}));

    expect(
      screen.queryByText(
        'Process instance modification mode allows you to plan multiple modifications on a process instance.',
      ),
    ).not.toBeInTheDocument();

    expect(modificationsStore.state.status).toBe('enabled');

    act(() => {
      modificationsStore.disableModificationMode();
    });

    expect(modificationsStore.state.status).toBe('disabled');

    await user.click(screen.getByTitle('Modify Instance instance_1'));

    expect(modificationsStore.state.status).toBe('enabled');

    expect(
      screen.queryByText(
        'Process instance modification mode allows you to plan multiple modifications on a process instance.',
      ),
    ).not.toBeInTheDocument();
  });
});
