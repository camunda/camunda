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

import {render, screen, waitFor, within} from 'modules/testing-library';
import {useEffect} from 'react';
import {processInstanceMigrationStore} from 'modules/stores/processInstanceMigration';
import {processXmlStore as processXmlMigrationSourceStore} from 'modules/stores/processXml/processXml.migration.source';
import {processXmlStore as processXmlMigrationTargetStore} from 'modules/stores/processXml/processXml.migration.target';
import {processStatisticsStore} from 'modules/stores/processStatistics/processStatistics.migration.source';
import {BottomPanel} from '.';
import {open} from 'modules/mocks/diagrams';
import {mockFetchProcessXML} from 'modules/mocks/api/processes/fetchProcessXML';

type Props = {
  children?: React.ReactNode;
};

const checkPayment = {
  id: 'checkPayment',
  name: 'Check payment',
  type: 'serviceTask',
};

const requestForPayment = {
  id: 'requestForPayment',
  name: 'Request for payment',
  type: 'serviceTask',
};

const shippingSubProcess = {
  id: 'shippingSubProcess',
  name: 'Shipping Sub Process',
  type: 'subProcess',
};

const shipArticles = {
  id: 'shipArticles',
  name: 'Ship Articles',
  type: 'userTask',
};

const confirmDelivery = {
  id: 'confirmDelivery',
  name: 'Confirm delivery',
  type: 'callActivity',
};

const Wrapper = ({children}: Props) => {
  processXmlMigrationSourceStore.setProcessXml(open('instanceMigration.bpmn'));
  processInstanceMigrationStore.enable();

  useEffect(() => {
    processInstanceMigrationStore.reset();
    processXmlMigrationSourceStore.reset();
    processXmlMigrationTargetStore.reset();
    processStatisticsStore.reset();
  });
  return (
    <>
      {children}
      <button
        onClick={() => {
          processXmlMigrationTargetStore.fetchProcessXml();
        }}
      >
        Fetch Target Process
      </button>
    </>
  );
};

describe('MigrationView/BottomPanel', () => {
  it('should render source flow nodes', async () => {
    render(<BottomPanel />, {wrapper: Wrapper});

    expect(
      await screen.findByRole('cell', {
        name: requestForPayment.name,
      }),
    ).toBeInTheDocument();

    expect(
      screen.getByRole('cell', {name: checkPayment.name}),
    ).toBeInTheDocument();

    expect(
      screen.getByRole('cell', {name: shipArticles.name}),
    ).toBeInTheDocument();

    expect(
      screen.getByRole('cell', {name: shippingSubProcess.name}),
    ).toBeInTheDocument();

    expect(
      screen.getByRole('cell', {name: confirmDelivery.name}),
    ).toBeInTheDocument();

    // expect table to have 1 header + 5 content rows
    expect(screen.getAllByRole('row')).toHaveLength(6);
  });

  it.each([
    {source: checkPayment, target: checkPayment},
    {source: shipArticles, target: shipArticles},
    {source: shippingSubProcess, target: shippingSubProcess},
    {source: confirmDelivery, target: confirmDelivery},
  ])(
    'should allow $source.type -> $target.type mapping',
    async ({source, target}) => {
      mockFetchProcessXML().withSuccess(open('instanceMigration.bpmn'));

      const {user} = render(<BottomPanel />, {wrapper: Wrapper});

      const combobox = await screen.findByRole('combobox', {
        name: new RegExp(`target flow node for ${source.name}`, 'i'),
      });

      expect(combobox).toBeDisabled();

      screen.getByRole('button', {name: /fetch target process/i}).click();
      await waitFor(() => {
        expect(combobox).toBeEnabled();
      });

      await user.selectOptions(combobox, target.name);
      expect(combobox).toHaveValue(target.id);

      await user.selectOptions(combobox, '');
      expect(combobox).toHaveValue('');
    },
  );

  it.each([
    {source: checkPayment, target: shipArticles},
    {source: checkPayment, target: shippingSubProcess},
    {source: checkPayment, target: confirmDelivery},
    {source: shipArticles, target: checkPayment},
    {source: shipArticles, target: shippingSubProcess},
    {source: shipArticles, target: confirmDelivery},
    {source: shippingSubProcess, target: checkPayment},
    {source: shippingSubProcess, target: shipArticles},
    {source: shippingSubProcess, target: confirmDelivery},
    {source: confirmDelivery, target: checkPayment},
    {source: confirmDelivery, target: shipArticles},
    {source: confirmDelivery, target: shippingSubProcess},
  ])(
    'should not allow $source.type -> $target.type mapping',
    async ({source, target}) => {
      mockFetchProcessXML().withSuccess(open('instanceMigration.bpmn'));

      render(<BottomPanel />, {wrapper: Wrapper});

      const combobox = await screen.findByRole('combobox', {
        name: new RegExp(`target flow node for ${source.name}`, 'i'),
      });

      expect(combobox).toBeDisabled();

      screen.getByRole('button', {name: /fetch target process/i}).click();

      await waitFor(() => {
        expect(combobox).toBeEnabled();
      });

      expect(
        within(combobox).getByRole('option', {name: source.name}),
      ).toBeInTheDocument();
      expect(
        within(combobox).queryByRole('option', {name: target.name}),
      ).not.toBeInTheDocument();
    },
  );
});
