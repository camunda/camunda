/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {render, screen, waitFor, within} from 'modules/testing-library';
import {BottomPanel} from '.';
import {open} from 'modules/mocks/diagrams';
import {mockFetchProcessXML} from 'modules/mocks/api/processes/fetchProcessXML';
import {elements, Wrapper} from './tests/mocks';

const {
  requestForPayment,
  checkPayment,
  shipArticles,
  shippingSubProcess,
  confirmDelivery,
  MessageInterrupting,
  TimerInterrupting,
  MessageNonInterrupting,
  TimerNonInterrupting,
  MessageIntermediateCatch,
  TimerIntermediateCatch,
} = elements;

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

    expect(
      screen.getByRole('cell', {
        name: new RegExp(`^${MessageInterrupting.name}`),
      }),
    ).toBeInTheDocument();

    expect(
      screen.getByRole('cell', {
        name: new RegExp(`^${TimerInterrupting.name}`),
      }),
    ).toBeInTheDocument();

    expect(
      screen.getByRole('cell', {
        name: new RegExp(`^${MessageNonInterrupting.name}`),
      }),
    ).toBeInTheDocument();

    expect(
      screen.getByRole('cell', {
        name: new RegExp(`^${TimerNonInterrupting.name}`),
      }),
    ).toBeInTheDocument();

    expect(
      screen.getByRole('cell', {
        name: new RegExp(`^${MessageIntermediateCatch.name}`),
      }),
    ).toBeInTheDocument();

    expect(
      screen.getByRole('cell', {
        name: new RegExp(`^${TimerIntermediateCatch.name}`),
      }),
    ).toBeInTheDocument();

    // expect table to have 1 header + 11 content rows
    expect(screen.getAllByRole('row')).toHaveLength(12);
  });

  it.each([
    {source: checkPayment, target: checkPayment},
    {source: shipArticles, target: shipArticles},
    {source: shippingSubProcess, target: shippingSubProcess},
    {source: confirmDelivery, target: confirmDelivery},
    {source: MessageInterrupting, target: MessageInterrupting},
    {source: MessageInterrupting, target: MessageNonInterrupting},
    {source: MessageIntermediateCatch, target: MessageIntermediateCatch},
    {source: TimerIntermediateCatch, target: TimerIntermediateCatch},
  ])(
    'should allow $source.type -> $target.type mapping',
    async ({source, target}) => {
      mockFetchProcessXML().withSuccess(open('instanceMigration.bpmn'));

      const {user} = render(<BottomPanel />, {wrapper: Wrapper});

      const combobox = await screen.findByRole('combobox', {
        name: new RegExp(`target flow node for ${source.name}`, 'i'),
      });

      expect(combobox).toBeDisabled();

      await user.click(
        screen.getByRole('button', {name: /fetch target process/i}),
      );

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
    {source: MessageInterrupting, target: TimerInterrupting},
    {source: TimerInterrupting, target: MessageNonInterrupting},
    {source: MessageIntermediateCatch, target: MessageInterrupting},
    {source: TimerInterrupting, target: TimerIntermediateCatch},
    {source: MessageIntermediateCatch, target: TimerIntermediateCatch},
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

    const comboboxMessageInterrupting = await screen.findByRole('combobox', {
      name: new RegExp(`target flow node for ${MessageInterrupting.name}`, 'i'),
    });

    const comboboxTimerInterrupting = await screen.findByRole('combobox', {
      name: new RegExp(`target flow node for ${TimerInterrupting.name}`, 'i'),
    });

    const comboboxMessageNonInterrupting = await screen.findByRole('combobox', {
      name: new RegExp(
        `target flow node for ${MessageNonInterrupting.name}`,
        'i',
      ),
    });

    const comboboxTimerNonInterrupting = await screen.findByRole('combobox', {
      name: new RegExp(
        `target flow node for ${TimerNonInterrupting.name}`,
        'i',
      ),
    });

    const comboboxTimerIntermediateCatch = await screen.findByRole('combobox', {
      name: new RegExp(
        `target flow node for ${TimerIntermediateCatch.name}`,
        'i',
      ),
    });

    const comboboxMessageIntermediateCatch = await screen.findByRole(
      'combobox',
      {
        name: new RegExp(
          `target flow node for ${MessageIntermediateCatch.name}`,
          'i',
        ),
      },
    );

    screen.getByRole('button', {name: /fetch target process/i}).click();

    await waitFor(() => {
      expect(comboboxCheckPayment).toBeEnabled();
    });

    // Expect auto-mapping (same id, same bpmn type)
    expect(comboboxCheckPayment).toHaveValue(checkPayment.id);

    // Expect auto-mapping (same id, same bpmn type)
    expect(comboboxShipArticles).toHaveValue(shipArticles.id);

    // Expect auto-mapping (same id, boundary event, same event type)
    expect(comboboxMessageInterrupting).toHaveValue(MessageInterrupting.id);
    expect(comboboxTimerNonInterrupting).toHaveValue(
      comboboxTimerNonInterrupting.id,
    );

    // Expect auto-mapping (same id, intermediate catch event, same event type)
    expect(comboboxMessageIntermediateCatch).toHaveValue(
      comboboxMessageIntermediateCatch.id,
    );

    // Expect no auto-mapping (flow node does not exist in target)
    expect(comboboxShippingSubProcess).toHaveValue('');
    expect(comboboxShippingSubProcess).toBeDisabled();

    expect(comboboxMessageNonInterrupting).toHaveValue('');
    expect(comboboxMessageNonInterrupting).toBeEnabled();

    expect(comboboxTimerInterrupting).toHaveValue('');
    expect(comboboxTimerInterrupting).toBeEnabled();

    expect(comboboxTimerIntermediateCatch).toHaveValue('');
    expect(comboboxTimerIntermediateCatch).toBeDisabled();

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

    const rowMessageInterrupting = screen.getByRole('row', {
      name: new RegExp(`^${MessageInterrupting.name}`),
    });

    const rowTimerInterrupting = screen.getByRole('row', {
      name: new RegExp(`^${TimerInterrupting.name}`),
    });

    const rowMessageNonInterrupting = screen.getByRole('row', {
      name: new RegExp(`^${MessageNonInterrupting.name}`),
    });

    const rowTimerNonInterrupting = screen.getByRole('row', {
      name: new RegExp(`^${TimerNonInterrupting.name}`),
    });

    await waitFor(() => {
      expect(comboboxRequestForPayment).toBeEnabled();
    });

    // expect to have no "not mapped" tag (auto-mapped)
    expect(
      within(rowCheckPayment).queryByText(/not mapped/i),
    ).not.toBeInTheDocument();
    expect(
      within(rowShipArticles).queryByText(/not mapped/i),
    ).not.toBeInTheDocument();
    expect(
      within(rowMessageInterrupting).queryByText(/not mapped/i),
    ).not.toBeInTheDocument();
    expect(
      within(rowTimerNonInterrupting).queryByText(/not mapped/i),
    ).not.toBeInTheDocument();
    expect(
      within(rowShippingSubProcess).getByText(/not mapped/i),
    ).toBeInTheDocument();

    // expect to have "not mapped" tag (not auto-mapped)
    expect(
      within(rowRequestForPayment).getByText(/not mapped/i),
    ).toBeInTheDocument();
    expect(
      within(rowMessageNonInterrupting).getByText(/not mapped/i),
    ).toBeInTheDocument();
    expect(
      within(rowTimerInterrupting).getByText(/not mapped/i),
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

    // Expect all 11 rows to be visible (+1 header row)
    expect(await screen.findAllByRole('row')).toHaveLength(12);

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
    expect(
      screen.queryByRole('row', {
        name: new RegExp(`^${MessageInterrupting.name}`),
      }),
    ).not.toBeInTheDocument();
    expect(
      screen.queryByRole('row', {
        name: new RegExp(`^${TimerNonInterrupting.name}`),
      }),
    ).not.toBeInTheDocument();
    expect(
      screen.queryByRole('row', {
        name: new RegExp(`^${MessageIntermediateCatch.name}`),
      }),
    ).not.toBeInTheDocument();

    // Expect 6 not mapped rows (+1 header row)
    expect(await screen.findAllByRole('row')).toHaveLength(7);

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
    expect(
      screen.getByRole('row', {
        name: new RegExp(`^${confirmDelivery.name}`),
      }),
    ).toBeInTheDocument();
    expect(
      screen.getByRole('row', {
        name: new RegExp(`^${MessageNonInterrupting.name}`),
      }),
    ).toBeInTheDocument();
    expect(
      screen.getByRole('row', {
        name: new RegExp(`^${TimerInterrupting.name}`),
      }),
    ).toBeInTheDocument();
    expect(
      screen.getByRole('row', {
        name: new RegExp(`^${TimerIntermediateCatch.name}`),
      }),
    ).toBeInTheDocument();

    expect(screen.getByLabelText(/show only not mapped/i)).toBeInTheDocument();
    expect(screen.getByLabelText(/show only not mapped/i)).toBeVisible();

    // Toggle off unmapped flow nodes
    await user.click(screen.getByLabelText(/show only not mapped/i));

    // Expect all rows to be visible again
    expect(await screen.findAllByRole('row')).toHaveLength(12);
  });
});
