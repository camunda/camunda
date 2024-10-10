/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {
  MatcherFunction,
  render,
  screen,
  waitFor,
  within,
} from 'modules/testing-library';
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
  TaskX,
  TaskY,
  MessageEventSubProcess,
  TimerEventSubProcess,
  MessageReceiveTask,
  BusinessRuleTask,
  ScriptTask,
  SendTask,
} = elements;

/**
 * Returns a custom matcher function which ignores all option elements from comboboxes.
 */
const getMatcherFunction = (flowNodeName: string): MatcherFunction => {
  return (content, element) => {
    return content === flowNodeName && element?.tagName !== 'OPTION';
  };
};

describe('MigrationView/BottomPanel', () => {
  it('should render source flow nodes', async () => {
    render(<BottomPanel />, {wrapper: Wrapper});

    expect(await screen.findByText(requestForPayment.name)).toBeInTheDocument();
    expect(screen.getByText(checkPayment.name)).toBeInTheDocument();
    expect(screen.getByText(shipArticles.name)).toBeInTheDocument();
    expect(screen.getByText(shippingSubProcess.name)).toBeInTheDocument();
    expect(screen.getByText(confirmDelivery.name)).toBeInTheDocument();
    expect(screen.getByText(MessageInterrupting.name)).toBeInTheDocument();
    expect(screen.getByText(TimerInterrupting.name)).toBeInTheDocument();
    expect(screen.getByText(MessageNonInterrupting.name)).toBeInTheDocument();
    expect(screen.getByText(TimerNonInterrupting.name)).toBeInTheDocument();
    expect(screen.getByText(MessageIntermediateCatch.name)).toBeInTheDocument();
    expect(screen.getByText(TimerIntermediateCatch.name)).toBeInTheDocument();
    expect(screen.getByText(TaskX.name)).toBeInTheDocument();
    expect(screen.getByText(TaskY.name)).toBeInTheDocument();
    expect(screen.getByText(MessageEventSubProcess.name)).toBeInTheDocument();
    expect(screen.getByText(TimerEventSubProcess.name)).toBeInTheDocument();
    expect(screen.getByText(MessageReceiveTask.name)).toBeInTheDocument();
    expect(screen.getByText(BusinessRuleTask.name)).toBeInTheDocument();
    expect(screen.getByText(ScriptTask.name)).toBeInTheDocument();
    expect(screen.getByText(SendTask.name)).toBeInTheDocument();

    // expect table to have 1 header + 19 content rows
    expect(screen.getAllByRole('row')).toHaveLength(20);
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
    {source: MessageEventSubProcess, target: MessageEventSubProcess},
    {source: TimerEventSubProcess, target: TimerEventSubProcess},
    {source: TaskX, target: TaskX},
    {source: BusinessRuleTask, target: BusinessRuleTask},
    {source: ScriptTask, target: ScriptTask},
    {source: SendTask, target: SendTask},
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
    {source: MessageEventSubProcess, target: TimerEventSubProcess},
    {source: checkPayment, target: BusinessRuleTask},
    {source: ScriptTask, target: SendTask},
    {source: SendTask, target: MessageReceiveTask},
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

    const comboboxCheckPayment = await screen.findByLabelText(
      new RegExp(`target flow node for ${checkPayment.name}`, 'i'),
    );
    const comboboxShippingSubProcess = await screen.findByLabelText(
      new RegExp(`target flow node for ${shippingSubProcess.name}`, 'i'),
    );
    const comboboxShipArticles = await screen.findByLabelText(
      new RegExp(`target flow node for ${shipArticles.name}`, 'i'),
    );
    const comboboxRequestForPayment = await screen.findByLabelText(
      new RegExp(`target flow node for ${requestForPayment.name}`, 'i'),
    );
    const comboboxMessageInterrupting = await screen.findByLabelText(
      new RegExp(`target flow node for ${MessageInterrupting.name}`, 'i'),
    );
    const comboboxTimerInterrupting = await screen.findByLabelText(
      new RegExp(`target flow node for ${TimerInterrupting.name}`, 'i'),
    );
    const comboboxMessageNonInterrupting = await screen.findByLabelText(
      new RegExp(`target flow node for ${MessageNonInterrupting.name}`, 'i'),
    );
    const comboboxTimerNonInterrupting = await screen.findByLabelText(
      new RegExp(`target flow node for ${TimerNonInterrupting.name}`, 'i'),
    );
    const comboboxTimerIntermediateCatch = await screen.findByLabelText(
      new RegExp(`target flow node for ${TimerIntermediateCatch.name}`, 'i'),
    );
    const comboboxMessageIntermediateCatch = await screen.findByLabelText(
      new RegExp(`target flow node for ${MessageIntermediateCatch.name}`, 'i'),
    );
    const comboboxMessageEventSubProcess = await screen.findByLabelText(
      new RegExp(`target flow node for ${MessageEventSubProcess.name}`, 'i'),
    );
    const comboboxTimerEventSubProcess = await screen.findByLabelText(
      new RegExp(`target flow node for ${TimerEventSubProcess.name}`, 'i'),
    );
    const comboboxTaskX = await screen.findByLabelText(
      new RegExp(`target flow node for ${TaskX.name}`, 'i'),
    );
    const comboboxTaskY = await screen.findByLabelText(
      new RegExp(`target flow node for ${TaskY.name}`, 'i'),
    );
    const comboboxMessageReceiveTask = await screen.findByLabelText(
      new RegExp(`target flow node for ${MessageReceiveTask.name}`, 'i'),
    );
    const comboboxBusinessRuleTask = await screen.findByLabelText(
      new RegExp(`target flow node for ${BusinessRuleTask.name}`, 'i'),
    );
    const comboboxScriptTaskTask = await screen.findByLabelText(
      new RegExp(`target flow node for ${ScriptTask.name}`, 'i'),
    );
    const comboboxSendTask = await screen.findByLabelText(
      new RegExp(`target flow node for ${SendTask.name}`, 'i'),
    );

    screen.getByRole('button', {name: /fetch target process/i}).click();

    await waitFor(() => {
      expect(comboboxCheckPayment).toBeEnabled();
    });

    // Expect auto-mapping (same id, same bpmn type)
    expect(comboboxCheckPayment).toHaveValue(checkPayment.id);
    expect(comboboxTaskX).toHaveValue(TaskX.id);
    expect(comboboxMessageReceiveTask).toHaveValue(MessageReceiveTask.id);
    expect(comboboxBusinessRuleTask).toHaveValue(BusinessRuleTask.id);
    expect(comboboxScriptTaskTask).toHaveValue(ScriptTask.id);
    expect(comboboxSendTask).toHaveValue(SendTask.id);
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

    // Expect auto-mapping (same event sub process type)
    expect(comboboxMessageEventSubProcess).toHaveValue(
      MessageEventSubProcess.id,
    );
    expect(comboboxTimerEventSubProcess).toHaveValue(TimerEventSubProcess.id);

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

    // Expect no auto-mapping (different id)
    expect(comboboxTaskY).toHaveValue('');
    expect(comboboxTaskY).toBeEnabled();
  });

  it('should add tags for unmapped flow nodes', async () => {
    mockFetchProcessXML().withSuccess(open('instanceMigration_v2.bpmn'));

    const {user} = render(<BottomPanel />, {wrapper: Wrapper});

    screen.getByRole('button', {name: /fetch target process/i}).click();

    const comboboxRequestForPayment = await screen.findByRole('combobox', {
      name: new RegExp(`target flow node for ${requestForPayment.name}`, 'i'),
    });

    const rowCheckPayment = screen
      .getByText(getMatcherFunction(checkPayment.name))
      .closest('tr');
    const rowShippingSubProcess = screen
      .getByText(getMatcherFunction(shippingSubProcess.name))
      .closest('tr');
    const rowShipArticles = screen
      .getByText(getMatcherFunction(shipArticles.name))
      .closest('tr');
    const rowRequestForPayment = screen
      .getByText(getMatcherFunction(requestForPayment.name))
      .closest('tr');
    const rowMessageInterrupting = screen
      .getByText(getMatcherFunction(MessageInterrupting.name))
      .closest('tr');
    const rowTimerInterrupting = screen
      .getByText(getMatcherFunction(TimerInterrupting.name))
      .closest('tr');
    const rowMessageNonInterrupting = screen
      .getByText(getMatcherFunction(MessageNonInterrupting.name))
      .closest('tr');
    const rowTimerNonInterrupting = screen
      .getByText(getMatcherFunction(TimerNonInterrupting.name))
      .closest('tr');
    const rowTaskY = screen
      .getByText(getMatcherFunction(TaskY.name))
      .closest('tr');
    const rowMessageEventSubProcess = screen
      .getByText(getMatcherFunction(MessageEventSubProcess.name))
      .closest('tr');
    const rowTimerEventSubProcess = screen
      .getByText(getMatcherFunction(TimerEventSubProcess.name))
      .closest('tr');
    const rowTaskX = screen
      .getByText(getMatcherFunction(TaskX.name))
      .closest('tr');
    const rowMessageReceiveTask = screen
      .getByText(getMatcherFunction(MessageReceiveTask.name))
      .closest('tr');
    const rowBusinessRuleTask = screen
      .getByText(getMatcherFunction(BusinessRuleTask.name))
      .closest('tr');
    const rowScriptTask = screen
      .getByText(getMatcherFunction(ScriptTask.name))
      .closest('tr');
    const rowSendTask = screen
      .getByText(getMatcherFunction(SendTask.name))
      .closest('tr');

    await waitFor(() => {
      expect(comboboxRequestForPayment).toBeEnabled();
    });

    // expect to have no "not mapped" tag (auto-mapped)
    expect(
      within(rowCheckPayment!).queryByText(/not mapped/i),
    ).not.toBeInTheDocument();
    expect(
      within(rowShipArticles!).queryByText(/not mapped/i),
    ).not.toBeInTheDocument();
    expect(
      within(rowMessageInterrupting!).queryByText(/not mapped/i),
    ).not.toBeInTheDocument();
    expect(
      within(rowTimerNonInterrupting!).queryByText(/not mapped/i),
    ).not.toBeInTheDocument();
    expect(
      within(rowMessageEventSubProcess!).queryByText(/not mapped/i),
    ).not.toBeInTheDocument();
    expect(
      within(rowTimerEventSubProcess!).queryByText(/not mapped/i),
    ).not.toBeInTheDocument();
    expect(
      within(rowTaskX!).queryByText(/not mapped/i),
    ).not.toBeInTheDocument();
    expect(
      within(rowMessageReceiveTask!).queryByText(/not mapped/i),
    ).not.toBeInTheDocument();
    expect(
      within(rowBusinessRuleTask!).queryByText(/not mapped/i),
    ).not.toBeInTheDocument();
    expect(
      within(rowScriptTask!).queryByText(/not mapped/i),
    ).not.toBeInTheDocument();
    expect(
      within(rowSendTask!).queryByText(/not mapped/i),
    ).not.toBeInTheDocument();

    // expect to have "not mapped" tag (not auto-mapped)
    expect(
      within(rowRequestForPayment!).getByText(/not mapped/i),
    ).toBeInTheDocument();
    expect(
      within(rowMessageNonInterrupting!).getByText(/not mapped/i),
    ).toBeInTheDocument();
    expect(
      within(rowTimerInterrupting!).getByText(/not mapped/i),
    ).toBeInTheDocument();
    expect(
      within(rowShippingSubProcess!).getByText(/not mapped/i),
    ).toBeInTheDocument();
    expect(within(rowTaskY!).getByText(/not mapped/i)).toBeInTheDocument();

    // expect tag not to be visible after selecting a target flow node
    await user.selectOptions(comboboxRequestForPayment, checkPayment.name);
    expect(
      within(rowRequestForPayment!).queryByText(/not mapped/i),
    ).not.toBeInTheDocument();

    // expect tag not to be visible after selecting a target flow node
    await user.selectOptions(comboboxRequestForPayment, '');
    expect(
      within(rowRequestForPayment!).getByText(/not mapped/i),
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

    // Expect all 19 rows to be visible (+1 header row)
    expect(await screen.findAllByRole('row')).toHaveLength(20);

    // Toggle on unmapped flow nodes
    await user.click(screen.getByLabelText(/show only not mapped/i));

    // Expect the following rows to be hidden (because they're mapped)
    expect(
      screen.queryByText(getMatcherFunction(checkPayment.name)),
    ).not.toBeInTheDocument();
    expect(
      screen.queryByText(getMatcherFunction(shipArticles.name)),
    ).not.toBeInTheDocument();
    expect(
      screen.queryByText(getMatcherFunction(MessageInterrupting.name)),
    ).not.toBeInTheDocument();
    expect(
      screen.queryByText(getMatcherFunction(TimerNonInterrupting.name)),
    ).not.toBeInTheDocument();
    expect(
      screen.queryByText(getMatcherFunction(MessageIntermediateCatch.name)),
    ).not.toBeInTheDocument();
    expect(
      screen.queryByText(getMatcherFunction(MessageEventSubProcess.name)),
    ).not.toBeInTheDocument();
    expect(
      screen.queryByText(getMatcherFunction(TimerEventSubProcess.name)),
    ).not.toBeInTheDocument();
    expect(
      screen.queryByText(getMatcherFunction(TaskX.name)),
    ).not.toBeInTheDocument();
    expect(
      screen.queryByText(getMatcherFunction(MessageReceiveTask.name)),
    ).not.toBeInTheDocument();
    expect(
      screen.queryByText(getMatcherFunction(BusinessRuleTask.name)),
    ).not.toBeInTheDocument();
    expect(
      screen.queryByText(getMatcherFunction(ScriptTask.name)),
    ).not.toBeInTheDocument();
    expect(
      screen.queryByText(getMatcherFunction(SendTask.name)),
    ).not.toBeInTheDocument();

    // Expect 6 not mapped rows (+1 header row)
    expect(await screen.findAllByRole('row')).toHaveLength(8);

    // Expect the following rows to be visible (because they're not mapped)
    expect(
      screen.getByText(getMatcherFunction(requestForPayment.name)),
    ).toBeInTheDocument();
    expect(
      screen.getByText(getMatcherFunction(shippingSubProcess.name)),
    ).toBeInTheDocument();
    expect(
      screen.getByText(getMatcherFunction(confirmDelivery.name)),
    ).toBeInTheDocument();
    expect(
      screen.getByText(getMatcherFunction(MessageNonInterrupting.name)),
    ).toBeInTheDocument();
    expect(
      screen.getByText(getMatcherFunction(TimerInterrupting.name)),
    ).toBeInTheDocument();
    expect(
      screen.getByText(getMatcherFunction(TimerIntermediateCatch.name)),
    ).toBeInTheDocument();
    expect(
      screen.getByText(getMatcherFunction(TaskY.name)),
    ).toBeInTheDocument();

    expect(screen.getByLabelText(/show only not mapped/i)).toBeInTheDocument();
    expect(screen.getByLabelText(/show only not mapped/i)).toBeVisible();

    // Toggle off unmapped flow nodes
    await user.click(screen.getByLabelText(/show only not mapped/i));

    // Expect all rows to be visible again
    expect(await screen.findAllByRole('row')).toHaveLength(20);
  });
});
