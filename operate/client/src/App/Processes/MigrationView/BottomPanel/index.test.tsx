/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
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
    return () => {
      processInstanceMigrationStore.reset();
      processXmlMigrationSourceStore.reset();
      processXmlMigrationTargetStore.reset();
      processStatisticsStore.reset();
    };
  }, []);

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
        name: new RegExp(`^${requestForPayment.name}`),
      }),
    ).toBeInTheDocument();

    expect(
      screen.getByRole('cell', {
        name: new RegExp(`^${checkPayment.name}`),
      }),
    ).toBeInTheDocument();

    expect(
      screen.getByRole('cell', {
        name: new RegExp(`^${shipArticles.name}`),
      }),
    ).toBeInTheDocument();

    expect(
      screen.getByRole('cell', {
        name: new RegExp(`^${shippingSubProcess.name}`),
      }),
    ).toBeInTheDocument();

    expect(
      screen.getByRole('cell', {name: new RegExp(`^${confirmDelivery.name}`)}),
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

  it('should auto-map flow nodes', async () => {
    mockFetchProcessXML().withSuccess(open('instanceMigration_v2.bpmn'));

    render(<BottomPanel />, {wrapper: Wrapper});

    const comboboxCheckPayment = await screen.findByRole('combobox', {
      name: new RegExp(`target flow node for ${checkPayment.name}`, 'i'),
    });

    const comboboxShippingSubProcess = await screen.findByRole('combobox', {
      name: new RegExp(`target flow node for ${shippingSubProcess.name}`, 'i'),
    });

    const comboboxShipArticles = await screen.findByRole('combobox', {
      name: new RegExp(`target flow node for ${shipArticles.name}`, 'i'),
    });

    const comboboxRequestForPayment = await screen.findByRole('combobox', {
      name: new RegExp(`target flow node for ${requestForPayment.name}`, 'i'),
    });

    screen.getByRole('button', {name: /fetch target process/i}).click();

    await waitFor(() => {
      expect(comboboxCheckPayment).toBeEnabled();
    });

    // Expect auto-mapping (same id, same bpmn type)
    expect(comboboxCheckPayment).toHaveValue(checkPayment.id);

    // Expect auto-mapping (same id, same bpmn type)
    expect(comboboxShipArticles).toHaveValue(shipArticles.id);

    // Expect no auto-mapping (flow node does not exist in target)
    expect(comboboxShippingSubProcess).toHaveValue('');
    expect(comboboxShippingSubProcess).toBeDisabled();

    // Expect no auto-mapping (different bpmn type)
    expect(comboboxRequestForPayment).toHaveValue('');
  });

  it('should add tags for unmapped flow nodes', async () => {
    mockFetchProcessXML().withSuccess(open('instanceMigration_v2.bpmn'));

    const {user} = render(<BottomPanel />, {wrapper: Wrapper});

    screen.getByRole('button', {name: /fetch target process/i}).click();

    const comboboxRequestForPayment = await screen.findByRole('combobox', {
      name: new RegExp(`target flow node for ${requestForPayment.name}`, 'i'),
    });

    const rowCheckPayment = screen.getByRole('row', {
      name: new RegExp(`^${checkPayment.name}`),
    });

    const rowShippingSubProcess = screen.getByRole('row', {
      name: new RegExp(`^${shippingSubProcess.name}`),
    });

    const rowShipArticles = screen.getByRole('row', {
      name: new RegExp(`^${shipArticles.name}`),
    });

    const rowRequestForPayment = screen.getByRole('row', {
      name: new RegExp(`^${requestForPayment.name}`),
    });

    await waitFor(() => {
      expect(comboboxRequestForPayment).toBeEnabled();
    });

    // expect to have no "not mapped" tag (auto-mapped)
    expect(
      within(rowCheckPayment).queryByText(/not mapped/i),
    ).not.toBeInTheDocument();

    // expect to have no "not mapped" tag (auto-mapped)
    expect(
      within(rowShipArticles).queryByText(/not mapped/i),
    ).not.toBeInTheDocument();

    // expect to have "not mapped" tag (not auto-mapped)
    expect(
      within(rowShippingSubProcess).getByText(/not mapped/i),
    ).toBeInTheDocument();

    // expect to have "not mapped" tag (not auto-mapped)
    expect(
      within(rowRequestForPayment).getByText(/not mapped/i),
    ).toBeInTheDocument();

    // expect tag not to be visible after selecting a target flow node
    await user.selectOptions(comboboxRequestForPayment, checkPayment.name);
    expect(
      within(rowRequestForPayment).queryByText(/not mapped/i),
    ).not.toBeInTheDocument();

    // expect tag not to be visible after selecting a target flow node
    await user.selectOptions(comboboxRequestForPayment, '');
    expect(
      within(rowRequestForPayment).getByText(/not mapped/i),
    ).toBeInTheDocument();
  });

  it('should hide mapped flow nodes', async () => {
    mockFetchProcessXML().withSuccess(open('instanceMigration_v2.bpmn'));

    const {user} = render(<BottomPanel />, {wrapper: Wrapper});

    screen.getByRole('button', {name: /fetch target process/i}).click();

    // wait for target combobox to be visible
    expect(
      await screen.findByRole('combobox', {
        name: new RegExp(`target flow node for ${requestForPayment.name}`, 'i'),
      }),
    ).toBeVisible();

    // Expect all rows to be visible
    expect(
      await screen.findByRole('row', {
        name: new RegExp(`^${requestForPayment.name}`),
      }),
    ).toBeInTheDocument();

    expect(
      screen.getByRole('row', {
        name: new RegExp(`^${checkPayment.name}`),
      }),
    ).toBeInTheDocument();

    expect(
      screen.getByRole('row', {
        name: new RegExp(`^${shipArticles.name}`),
      }),
    ).toBeInTheDocument();

    expect(
      screen.getByRole('row', {
        name: new RegExp(`^${shippingSubProcess.name}`),
      }),
    ).toBeInTheDocument();

    // Toggle on unmapped flow nodes
    await user.click(screen.getByLabelText(/show only not mapped/i));

    // Expect the following rows to be hidden (because they're mapped)
    expect(
      screen.queryByRole('row', {
        name: new RegExp(`^${checkPayment.name}`),
      }),
    ).not.toBeInTheDocument();
    expect(
      screen.queryByRole('row', {
        name: new RegExp(`^${shipArticles.name}`),
      }),
    ).not.toBeInTheDocument();

    // Expect the following rows to be visible (because they're not mapped)
    expect(
      screen.getByRole('row', {
        name: new RegExp(`^${requestForPayment.name}`),
      }),
    ).toBeInTheDocument();
    expect(
      screen.getByRole('row', {
        name: new RegExp(`^${shippingSubProcess.name}`),
      }),
    ).toBeInTheDocument();

    // Toggle off unmapped flow nodes
    await user.click(screen.getByLabelText(/show only not mapped/i));

    // Expect all rows to be visible again
    expect(
      await screen.findByRole('row', {
        name: new RegExp(`^${requestForPayment.name}`),
      }),
    ).toBeInTheDocument();
    expect(
      screen.getByRole('row', {
        name: new RegExp(`^${checkPayment.name}`),
      }),
    ).toBeInTheDocument();
    expect(
      screen.getByRole('row', {
        name: new RegExp(`^${shipArticles.name}`),
      }),
    ).toBeInTheDocument();
    expect(
      screen.getByRole('row', {
        name: new RegExp(`^${shippingSubProcess.name}`),
      }),
    ).toBeInTheDocument();
  });
});
