/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
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

const FLOW_NODES = {
  shipArticles: 'Ship Articles',
  requestForPayment: 'Request for payment',
  checkPayment: 'Check payment',
};

const Wrapper = ({children}: Props) => {
  processXmlMigrationSourceStore.setProcessXml(open('orderProcess.bpmn'));
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
        name: FLOW_NODES.requestForPayment,
      }),
    ).toBeInTheDocument();

    expect(
      screen.getByRole('cell', {name: FLOW_NODES.shipArticles}),
    ).toBeInTheDocument();

    expect(
      screen.getByRole('cell', {name: FLOW_NODES.checkPayment}),
    ).toBeInTheDocument();

    // expect table to have 1 header + 3 content rows
    expect(screen.getAllByRole('row')).toHaveLength(4);
  });

  it('should allow service task mapping', async () => {
    mockFetchProcessXML().withSuccess(open('orderProcess.bpmn'));

    const {user} = render(<BottomPanel />, {wrapper: Wrapper});

    const combobox = await screen.findByRole('combobox', {
      name: new RegExp(
        `target flow node for ${FLOW_NODES.requestForPayment}`,
        'i',
      ),
    });

    expect(combobox).toBeDisabled();

    screen.getByRole('button', {name: /fetch target process/i}).click();
    await waitFor(() => {
      expect(combobox).toBeEnabled();
    });

    await user.selectOptions(combobox, FLOW_NODES.requestForPayment);
    expect(combobox).toHaveValue('requestForPayment');

    await user.selectOptions(combobox, '');
    expect(combobox).toHaveValue('');
  });

  it('should allow user task mapping', async () => {
    mockFetchProcessXML().withSuccess(open('orderProcess.bpmn'));

    const {user} = render(<BottomPanel />, {wrapper: Wrapper});

    const combobox = await screen.findByRole('combobox', {
      name: new RegExp(`target flow node for ${FLOW_NODES.shipArticles}`, 'i'),
    });

    expect(combobox).toBeDisabled();

    screen.getByRole('button', {name: /fetch target process/i}).click();
    await waitFor(() => {
      expect(combobox).toBeEnabled();
    });

    await user.selectOptions(combobox, FLOW_NODES.shipArticles);
    expect(combobox).toHaveValue('shipArticles');

    await user.selectOptions(combobox, '');
    expect(combobox).toHaveValue('');
  });

  it('should not allow user task -> service task mapping', async () => {
    mockFetchProcessXML().withSuccess(open('orderProcess.bpmn'));

    render(<BottomPanel />, {wrapper: Wrapper});

    const combobox = await screen.findByRole('combobox', {
      name: new RegExp(`target flow node for ${FLOW_NODES.shipArticles}`, 'i'),
    });

    expect(combobox).toBeDisabled();

    screen.getByRole('button', {name: /fetch target process/i}).click();

    await waitFor(() => {
      expect(combobox).toBeEnabled();
    });

    expect(
      within(combobox).getByRole('option', {name: FLOW_NODES.shipArticles}),
    ).toBeInTheDocument();
    expect(
      within(combobox).queryByRole('option', {name: FLOW_NODES.checkPayment}),
    ).not.toBeInTheDocument();
  });

  it('should not allow service task -> user task mapping', async () => {
    mockFetchProcessXML().withSuccess(open('orderProcess.bpmn'));

    render(<BottomPanel />, {wrapper: Wrapper});

    const combobox = await screen.findByRole('combobox', {
      name: new RegExp(`target flow node for ${FLOW_NODES.checkPayment}`, 'i'),
    });

    screen.getByRole('button', {name: /fetch target process/i}).click();

    await waitFor(() => {
      expect(combobox).toBeEnabled();
    });

    expect(
      within(combobox).getByRole('option', {name: FLOW_NODES.checkPayment}),
    ).toBeInTheDocument();
    expect(
      within(combobox).queryByRole('option', {name: FLOW_NODES.shipArticles}),
    ).not.toBeInTheDocument();
  });
});
