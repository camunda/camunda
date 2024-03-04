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

import {render, screen, waitFor} from 'modules/testing-library';
import {mockDmnXml} from 'modules/mocks/mockDmnXml';
import {groupedDecisions} from 'modules/mocks/groupedDecisions';
import {Decision} from '..';
import {mockFetchDecisionXML} from 'modules/mocks/api/decisions/fetchDecisionXML';
import {mockFetchGroupedDecisions} from 'modules/mocks/api/decisions/fetchGroupedDecisions';
import {createWrapper} from './mocks';
import {groupedDecisionsStore} from 'modules/stores/groupedDecisions';

describe('<Decision />', () => {
  beforeEach(() => {
    mockFetchGroupedDecisions().withSuccess(groupedDecisions);
  });

  it('should render decision table and panel header', async () => {
    const originalWindowPrompt = window.prompt;
    window.prompt = jest.fn();

    mockFetchDecisionXML().withSuccess(mockDmnXml);

    const {user} = render(<Decision />, {
      wrapper: createWrapper('/decisions?name=invoiceClassification&version=1'),
    });

    expect(
      await screen.findByText('DecisionTable view mock'),
    ).toBeInTheDocument();
    expect(screen.getByRole('heading', {name: 'invoiceClassification'}));
    expect(screen.getAllByText('invoiceClassification')).toHaveLength(2);

    await user.click(
      screen.getByRole('button', {
        name: 'Decision ID / Click to copy',
      }),
    );

    expect(await screen.findByText('Copied to clipboard')).toBeInTheDocument();

    window.prompt = originalWindowPrompt;
  });

  it('should render text when no decision is selected', async () => {
    mockFetchDecisionXML().withSuccess(mockDmnXml);

    render(<Decision />, {
      wrapper: createWrapper('/decisions'),
    });

    expect(
      await screen.findByText(/there is no decision selected/i),
    ).toBeInTheDocument();
    expect(
      screen.getByText(
        /to see a decision table or a literal expression, select a decision in the filters panel/i,
      ),
    ).toBeInTheDocument();
    expect(screen.getByRole('heading', {name: 'Decision'}));

    await waitFor(() =>
      expect(groupedDecisionsStore.state.status).toBe('fetched'),
    );
  });

  it('should render text when no version is selected', async () => {
    mockFetchDecisionXML().withSuccess(mockDmnXml);

    render(<Decision />, {
      wrapper: createWrapper(
        '/decisions?name=invoiceClassification&version=all',
      ),
    });

    expect(
      await screen.findByText(
        /there is more than one version selected for decision "invoiceClassification"/i,
      ),
    ).toBeInTheDocument();
    expect(
      screen.getByText(
        /to see a decision table or a literal expression, select a single version/i,
      ),
    ).toBeInTheDocument();
    expect(screen.getByRole('heading', {name: 'invoiceClassification'}));
  });

  it('should render text on error', async () => {
    mockFetchDecisionXML().withServerError(404);

    render(<Decision />, {
      wrapper: createWrapper('/decisions?name=calc-key-figures&version=1'),
    });

    expect(
      await screen.findByText(/data could not be fetched/i),
    ).toBeInTheDocument();
    expect(
      screen.getByRole('heading', {
        name: 'Calculate Credit History Key Figures',
      }),
    );
  });
});
