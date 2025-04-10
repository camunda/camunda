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
import {elements, SOURCE_PROCESS_DEFINITION_KEY, Wrapper} from './tests/mocks';
import {mockFetchProcessDefinitionXml} from 'modules/mocks/api/v2/processDefinitions/fetchProcessDefinitionXml';

const TARGET_PROCESS_DEFINITION_KEY = '2';

jest.mock('modules/stores/processes/processes.migration', () => ({
  processesStore: {
    migrationState: {selectedTargetProcess: {bpmnProcessId: 'orderProcess'}},
    getSelectedProcessDetails: () => ({bpmnProcessId: 'orderProcess'}),
    selectedTargetProcessId: TARGET_PROCESS_DEFINITION_KEY,
  },
}));

const {
  requestForPayment,
  ExclusiveGateway,
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
  TimerStartEvent,
  MessageReceiveTask,
  BusinessRuleTask,
  ScriptTask,
  SendTask,
  EventBasedGateway,
  IntermediateTimerEvent,
  SignalIntermediateCatch,
  SignalBoundaryEvent,
  SignalEventSubProcess,
  SignalStartEvent,
  ErrorEventSubProcess,
  ErrorStartEvent,
  MultiInstanceSubProcess,
  MultiInstanceTask,
  EscalationEventSubProcess,
  EscalationStartEvent,
  CompensationTask,
  CompensationBoundaryEvent,
  MessageStartEvent,
  ParallelGateway_1,
  ParallelGateway_2,
  ParallelGateway_3,
  ParallelGateway_4,
} = elements;

const HEADER_ROW_COUNT = 1;
const CONTENT_ROW_COUNT = 40;

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
    mockFetchProcessDefinitionXml().withSuccess(open('instanceMigration.bpmn'));
    mockFetchProcessDefinitionXml().withSuccess(open('instanceMigration.bpmn'));

    render(<BottomPanel />, {wrapper: Wrapper});

    expect(
      await screen.findByText(getMatcherFunction(requestForPayment.name)),
    ).toBeInTheDocument();
    expect(
      screen.getByText(getMatcherFunction(checkPayment.name)),
    ).toBeInTheDocument();
    expect(
      screen.getByText(getMatcherFunction(checkPayment.name)),
    ).toBeInTheDocument();
    expect(
      screen.getByText(getMatcherFunction(ExclusiveGateway.name)),
    ).toBeInTheDocument();
    expect(
      screen.getByText(getMatcherFunction(shipArticles.name)),
    ).toBeInTheDocument();
    expect(
      screen.getByText(getMatcherFunction(shippingSubProcess.name)),
    ).toBeInTheDocument();
    expect(
      screen.getByText(getMatcherFunction(confirmDelivery.name)),
    ).toBeInTheDocument();
    expect(
      screen.getByText(getMatcherFunction(MessageInterrupting.name)),
    ).toBeInTheDocument();
    expect(
      screen.getByText(getMatcherFunction(TimerInterrupting.name)),
    ).toBeInTheDocument();
    expect(
      screen.getByText(getMatcherFunction(MessageNonInterrupting.name)),
    ).toBeInTheDocument();
    expect(
      screen.getByText(getMatcherFunction(TimerNonInterrupting.name)),
    ).toBeInTheDocument();
    expect(
      screen.getByText(getMatcherFunction(MessageIntermediateCatch.name)),
    ).toBeInTheDocument();
    expect(
      screen.getByText(getMatcherFunction(TimerIntermediateCatch.name)),
    ).toBeInTheDocument();
    expect(
      screen.getByText(getMatcherFunction(TaskX.name)),
    ).toBeInTheDocument();
    expect(
      screen.getByText(getMatcherFunction(TaskY.name)),
    ).toBeInTheDocument();
    expect(
      screen.getByText(getMatcherFunction(MessageEventSubProcess.name)),
    ).toBeInTheDocument();
    expect(
      screen.getByText(getMatcherFunction(TimerEventSubProcess.name)),
    ).toBeInTheDocument();
    expect(
      screen.getByText(getMatcherFunction(MessageReceiveTask.name)),
    ).toBeInTheDocument();
    expect(
      screen.getByText(getMatcherFunction(BusinessRuleTask.name)),
    ).toBeInTheDocument();
    expect(
      screen.getByText(getMatcherFunction(ScriptTask.name)),
    ).toBeInTheDocument();
    expect(
      screen.getByText(getMatcherFunction(SendTask.name)),
    ).toBeInTheDocument();
    expect(
      screen.getByText(getMatcherFunction(TimerStartEvent.name)),
    ).toBeInTheDocument();
    expect(
      screen.getByText(getMatcherFunction(EventBasedGateway.name)),
    ).toBeInTheDocument();
    expect(
      screen.getByText(getMatcherFunction(IntermediateTimerEvent.name)),
    ).toBeInTheDocument();
    expect(
      screen.getByText(getMatcherFunction(SignalIntermediateCatch.name)),
    ).toBeInTheDocument();
    expect(
      screen.getByText(getMatcherFunction(SignalBoundaryEvent.name)),
    ).toBeInTheDocument();
    expect(
      screen.getByText(getMatcherFunction(SignalEventSubProcess.name)),
    ).toBeInTheDocument();
    expect(
      screen.getByText(getMatcherFunction(SignalStartEvent.name)),
    ).toBeInTheDocument();
    expect(
      screen.getByText(getMatcherFunction(ErrorEventSubProcess.name)),
    ).toBeInTheDocument();
    expect(
      screen.getByText(getMatcherFunction(ErrorStartEvent.name)),
    ).toBeInTheDocument();
    expect(
      screen.getByText(getMatcherFunction(EscalationEventSubProcess.name)),
    ).toBeInTheDocument();
    expect(
      screen.getByText(getMatcherFunction(EscalationStartEvent.name)),
    ).toBeInTheDocument();
    expect(
      screen.getByText(getMatcherFunction(CompensationTask.name)),
    ).toBeInTheDocument();
    expect(
      screen.getByText(getMatcherFunction(CompensationBoundaryEvent.name)),
    ).toBeInTheDocument();
    expect(
      screen.getByText(getMatcherFunction(MessageStartEvent.name)),
    ).toBeInTheDocument();
    expect(
      screen.getByText(getMatcherFunction(ParallelGateway_1.name)),
    ).toBeInTheDocument();
    expect(
      screen.getByText(getMatcherFunction(ParallelGateway_2.name)),
    ).toBeInTheDocument();
    expect(
      screen.getByText(getMatcherFunction(ParallelGateway_3.name)),
    ).toBeInTheDocument();
    expect(
      screen.getByText(getMatcherFunction(ParallelGateway_4.name)),
    ).toBeInTheDocument();

    expect(screen.getAllByRole('row')).toHaveLength(
      HEADER_ROW_COUNT + CONTENT_ROW_COUNT,
    );
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
    {source: TimerStartEvent, target: TimerStartEvent},
    {source: SignalIntermediateCatch, target: SignalIntermediateCatch},
    {source: ErrorEventSubProcess, target: ErrorEventSubProcess},
    {source: EscalationEventSubProcess, target: EscalationEventSubProcess},
  ])(
    'should allow $source.type -> $target.type mapping',
    async ({source, target}) => {
      mockFetchProcessDefinitionXml().withSuccess(
        open('instanceMigration.bpmn'),
      );
      mockFetchProcessDefinitionXml().withSuccess(
        open('instanceMigration.bpmn'),
      );

      const {user} = render(<BottomPanel />, {wrapper: Wrapper});

      const combobox = await screen.findByRole('combobox', {
        name: new RegExp(`target flow node for ${source.name}`, 'i'),
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
    {source: TimerStartEvent, target: TimerIntermediateCatch},
    {source: SignalIntermediateCatch, target: SignalBoundaryEvent},
    {source: MessageIntermediateCatch, target: SignalIntermediateCatch},
    {source: MultiInstanceTask, target: SendTask},
    {source: CompensationBoundaryEvent, target: SignalBoundaryEvent},
  ])(
    'should not allow $source.type -> $target.type mapping',
    async ({source, target}) => {
      mockFetchProcessDefinitionXml().withSuccess(
        open('instanceMigration.bpmn'),
      );
      mockFetchProcessDefinitionXml().withSuccess(
        open('instanceMigration.bpmn'),
      );

      render(<BottomPanel />, {wrapper: Wrapper});

      const combobox = await screen.findByRole('combobox', {
        name: new RegExp(`target flow node for ${source.name}`, 'i'),
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
    // source process definition
    mockFetchProcessDefinitionXml({
      processDefinitionKey: SOURCE_PROCESS_DEFINITION_KEY,
    }).withSuccess(open('instanceMigration.bpmn'));
    // target process definition
    mockFetchProcessDefinitionXml({
      processDefinitionKey: TARGET_PROCESS_DEFINITION_KEY,
    }).withSuccess(open('instanceMigration_v2.bpmn'));

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
    const comboboxTimerStartEvent = await screen.findByLabelText(
      new RegExp(`target flow node for ${TimerStartEvent.name}`, 'i'),
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
    const comboboxSignalIntermediateCatch = await screen.findByLabelText(
      new RegExp(`target flow node for ${SignalIntermediateCatch.name}`, 'i'),
    );
    const comboboxSignalBoundaryEvent = await screen.findByLabelText(
      new RegExp(`target flow node for ${SignalBoundaryEvent.name}`, 'i'),
    );
    const comboboxSignalEventSubProcess = await screen.findByLabelText(
      new RegExp(`target flow node for ${SignalEventSubProcess.name}`, 'i'),
    );
    const comboboxSignalStartEvent = await screen.findByLabelText(
      new RegExp(`target flow node for ${SignalStartEvent.name}`, 'i'),
    );
    const comboboxErrorEventSubProcess = await screen.findByLabelText(
      new RegExp(`target flow node for ${ErrorEventSubProcess.name}`, 'i'),
    );
    const comboboxErrorStartEvent = await screen.findByLabelText(
      new RegExp(`target flow node for ${ErrorStartEvent.name}`, 'i'),
    );
    const comboboxMultiInstanceSubProcess = await screen.findByLabelText(
      new RegExp(`target flow node for ${MultiInstanceSubProcess.name}`, 'i'),
    );
    const comboboxEscalationEventSubProcess = await screen.findByLabelText(
      new RegExp(`target flow node for ${EscalationEventSubProcess.name}`, 'i'),
    );
    const comboboxEscalationStartEvent = await screen.findByLabelText(
      new RegExp(`target flow node for ${EscalationStartEvent.name}`, 'i'),
    );
    const comboboxCompensationBoundaryEvent = await screen.findByLabelText(
      new RegExp(`target flow node for ${CompensationBoundaryEvent.name}`, 'i'),
    );
    const comboboxMessageStartEvent = await screen.findByLabelText(
      new RegExp(`target flow node for ${MessageStartEvent.name}`, 'i'),
    );

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

    // Expect auto-mapping (same id, start event, same event type)
    expect(comboboxTimerStartEvent).toHaveValue(TimerStartEvent.id);
    expect(comboboxSignalStartEvent).toHaveValue(SignalStartEvent.id);
    expect(comboboxErrorStartEvent).toHaveValue(comboboxErrorStartEvent.id);
    expect(comboboxEscalationStartEvent).toHaveValue(
      comboboxEscalationStartEvent.id,
    );
    expect(comboboxMessageStartEvent).toHaveValue(comboboxMessageStartEvent.id);

    // Expect auto-mapping (same id, boundary event, same event type)
    expect(comboboxMessageInterrupting).toHaveValue(MessageInterrupting.id);
    expect(comboboxTimerNonInterrupting).toHaveValue(
      comboboxTimerNonInterrupting.id,
    );
    expect(comboboxSignalBoundaryEvent).toHaveValue(SignalBoundaryEvent.id);
    expect(comboboxCompensationBoundaryEvent).toHaveValue(
      comboboxCompensationBoundaryEvent.id,
    );

    // Expect auto-mapping (same id, intermediate catch event, same event type)
    expect(comboboxMessageIntermediateCatch).toHaveValue(
      comboboxMessageIntermediateCatch.id,
    );
    expect(comboboxSignalIntermediateCatch).toHaveValue(
      SignalIntermediateCatch.id,
    );

    // Expect auto-mapping (same event sub process type)
    expect(comboboxMessageEventSubProcess).toHaveValue(
      MessageEventSubProcess.id,
    );
    expect(comboboxTimerEventSubProcess).toHaveValue(TimerEventSubProcess.id);
    expect(comboboxSignalEventSubProcess).toHaveValue(SignalEventSubProcess.id);
    expect(comboboxErrorEventSubProcess).toHaveValue(ErrorEventSubProcess.id);
    expect(comboboxEscalationEventSubProcess).toHaveValue(
      EscalationEventSubProcess.id,
    );

    // Expect auto-mapping (same multi instance type)
    expect(comboboxMultiInstanceSubProcess).toHaveValue(
      MultiInstanceSubProcess.id,
    );

    // Expect no auto-mapping (flow node does not exist in target)
    expect(comboboxShippingSubProcess).toHaveValue('');

    expect(comboboxMessageNonInterrupting).toHaveValue('');
    expect(comboboxMessageNonInterrupting).toBeEnabled();

    expect(comboboxTimerInterrupting).toHaveValue('');
    expect(comboboxTimerInterrupting).toBeEnabled();

    expect(comboboxTimerIntermediateCatch).toHaveValue('');

    // Expect no auto-mapping (different bpmn type)
    expect(comboboxRequestForPayment).toHaveValue('');

    // Expect no auto-mapping (different id)
    expect(comboboxTaskY).toHaveValue('');
    expect(comboboxTaskY).toBeEnabled();
  });

  it('should add tags for unmapped flow nodes', async () => {
    // source process definition
    mockFetchProcessDefinitionXml({
      processDefinitionKey: SOURCE_PROCESS_DEFINITION_KEY,
    }).withSuccess(open('instanceMigration.bpmn'));
    // target process definition
    mockFetchProcessDefinitionXml({
      processDefinitionKey: TARGET_PROCESS_DEFINITION_KEY,
    }).withSuccess(open('instanceMigration_v2.bpmn'));

    const {user} = render(<BottomPanel />, {wrapper: Wrapper});

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
    const rowTimerStartEvent = screen
      .getByText(getMatcherFunction(TimerStartEvent.name))
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
    const rowSignalIntermediateCatch = screen
      .getByText(getMatcherFunction(SignalIntermediateCatch.name))
      .closest('tr');
    const rowSignalBoundaryEvent = screen
      .getByText(getMatcherFunction(SignalBoundaryEvent.name))
      .closest('tr');
    const rowSignalEventSubProcess = screen
      .getByText(getMatcherFunction(SignalEventSubProcess.name))
      .closest('tr');
    const rowSignalStartEvent = screen
      .getByText(getMatcherFunction(SignalStartEvent.name))
      .closest('tr');
    const rowErrorEventSubProcess = screen
      .getByText(getMatcherFunction(ErrorEventSubProcess.name))
      .closest('tr');
    const rowErrorStartEvent = screen
      .getByText(getMatcherFunction(ErrorEventSubProcess.name))
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
      within(rowTimerStartEvent!).queryByText(/not mapped/i),
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
    expect(
      within(rowSignalIntermediateCatch!).queryByText(/not mapped/i),
    ).not.toBeInTheDocument();
    expect(
      within(rowSignalBoundaryEvent!).queryByText(/not mapped/i),
    ).not.toBeInTheDocument();
    expect(
      within(rowSignalEventSubProcess!).queryByText(/not mapped/i),
    ).not.toBeInTheDocument();
    expect(
      within(rowSignalStartEvent!).queryByText(/not mapped/i),
    ).not.toBeInTheDocument();
    expect(
      within(rowErrorEventSubProcess!).queryByText(/not mapped/i),
    ).not.toBeInTheDocument();
    expect(
      within(rowErrorStartEvent!).queryByText(/not mapped/i),
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
    mockFetchProcessDefinitionXml({
      processDefinitionKey: SOURCE_PROCESS_DEFINITION_KEY,
    }).withSuccess(open('instanceMigration.bpmn'));
    mockFetchProcessDefinitionXml({
      processDefinitionKey: TARGET_PROCESS_DEFINITION_KEY,
    }).withSuccess(open('instanceMigration_v2.bpmn'));

    const {user} = render(<BottomPanel />, {wrapper: Wrapper});

    // wait for target combobox to be visible
    expect(
      await screen.findByRole('combobox', {
        name: new RegExp(`target flow node for ${requestForPayment.name}`, 'i'),
      }),
    ).toBeVisible();

    // Expect all rows to be visible
    expect(await screen.findAllByRole('row')).toHaveLength(
      HEADER_ROW_COUNT + CONTENT_ROW_COUNT,
    );

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
      screen.queryByText(getMatcherFunction(TimerStartEvent.name)),
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
    expect(
      screen.queryByText(getMatcherFunction(EventBasedGateway.name)),
    ).not.toBeInTheDocument();
    expect(
      screen.queryByText(getMatcherFunction(ExclusiveGateway.name)),
    ).not.toBeInTheDocument();
    expect(
      screen.queryByText(getMatcherFunction(IntermediateTimerEvent.name)),
    ).not.toBeInTheDocument();
    expect(
      screen.queryByText(getMatcherFunction(SignalIntermediateCatch.name)),
    ).not.toBeInTheDocument();
    expect(
      screen.queryByText(getMatcherFunction(SignalBoundaryEvent.name)),
    ).not.toBeInTheDocument();
    expect(
      screen.queryByText(getMatcherFunction(SignalEventSubProcess.name)),
    ).not.toBeInTheDocument();
    expect(
      screen.queryByText(getMatcherFunction(SignalStartEvent.name)),
    ).not.toBeInTheDocument();
    expect(
      screen.queryByText(getMatcherFunction(ErrorEventSubProcess.name)),
    ).not.toBeInTheDocument();
    expect(
      screen.queryByText(getMatcherFunction(MultiInstanceSubProcess.name)),
    ).not.toBeInTheDocument();
    expect(
      screen.queryByText(getMatcherFunction(CompensationTask.name)),
    ).not.toBeInTheDocument();
    expect(
      screen.queryByText(getMatcherFunction(CompensationBoundaryEvent.name)),
    ).not.toBeInTheDocument();

    const UNMAPPED_ROW_COUNT = 8;

    expect(await screen.findAllByRole('row')).toHaveLength(
      HEADER_ROW_COUNT + UNMAPPED_ROW_COUNT,
    );

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
      screen.getByText(getMatcherFunction(MultiInstanceTask.name)),
    ).toBeInTheDocument();
    expect(
      screen.getByText(getMatcherFunction(TaskY.name)),
    ).toBeInTheDocument();

    expect(screen.getByLabelText(/show only not mapped/i)).toBeInTheDocument();
    expect(screen.getByLabelText(/show only not mapped/i)).toBeVisible();

    // Toggle off unmapped flow nodes
    await user.click(screen.getByLabelText(/show only not mapped/i));

    // Expect all rows to be visible again
    expect(await screen.findAllByRole('row')).toHaveLength(
      HEADER_ROW_COUNT + CONTENT_ROW_COUNT,
    );
  });
});
