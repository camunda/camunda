/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useEffect} from 'react';
import {processInstanceMigrationStore} from 'modules/stores/processInstanceMigration';
import {processStatisticsStore} from 'modules/stores/processStatistics/processStatistics.migration.source';
import {getMockQueryClient} from 'modules/react-query/mockQueryClient';
import {QueryClientProvider} from '@tanstack/react-query';

const elements = {
  checkPayment: {
    id: 'checkPayment',
    name: 'Check payment',
    type: 'serviceTask',
  },
  ExclusiveGateway: {
    id: 'ExclusiveGateway',
    name: 'Payment OK?',
    type: 'exclusiveGateway',
  },
  requestForPayment: {
    id: 'requestForPayment',
    name: 'Request for payment',
    type: 'serviceTask',
  },
  shippingSubProcess: {
    id: 'shippingSubProcess',
    name: 'Shipping Sub Process',
    type: 'subProcess',
  },
  shipArticles: {
    id: 'shipArticles',
    name: 'Ship Articles',
    type: 'userTask',
  },
  confirmDelivery: {
    id: 'confirmDelivery',
    name: 'Confirm delivery',
    type: 'callActivity',
  },
  MessageInterrupting: {
    id: 'MessageInterrupting',
    name: 'Message interrupting',
    type: 'messageBoundaryEventInterrupting',
  },
  TimerInterrupting: {
    id: 'TimerInterrupting',
    name: 'Timer interrupting',
    type: 'timerBoundaryEventInterrupting',
  },
  MessageNonInterrupting: {
    id: 'MessageNonInterrupting',
    name: 'Message non-interrupting',
    type: 'messageBoundaryEventNonInterrupting',
  },
  TimerNonInterrupting: {
    id: 'TimerNonInterrupting',
    name: 'Timer non-interrupting',
    type: 'timerBoundaryEventNonInterrupting',
  },
  MessageIntermediateCatch: {
    id: 'MessageIntermediateCatch',
    name: 'Message intermediate catch',
    type: 'messageIntermediateCatch',
  },
  TimerIntermediateCatch: {
    id: 'TimerIntermediateCatch',
    name: 'Timer intermediate catch',
    type: 'timerIntermediateCatch',
  },
  TaskX: {
    id: 'TaskX',
    name: 'Task X',
    type: 'serviceTask',
  },
  TaskY: {
    id: 'TaskY',
    name: 'Task Y',
    type: 'serviceTask',
  },
  MessageEventSubProcess: {
    id: 'MessageEventSubProcess',
    name: 'Message event sub process',
    type: 'eventSubProcess',
  },
  MessageStartEvent: {
    id: 'MessageStartEvent',
    name: 'Message start event',
    type: 'startEvent',
  },
  TimerEventSubProcess: {
    id: 'TimerEventSubProcess',
    name: 'Timer event sub process',
    type: 'eventSubProcess',
  },
  TimerStartEvent: {
    id: 'TimerStartEvent',
    name: 'Timer start event',
    type: 'startEvent',
  },
  MessageReceiveTask: {
    id: 'MessageReceiveTask',
    name: 'Message receive task',
    type: 'receiveTask',
  },
  BusinessRuleTask: {
    id: 'BusinessRuleTask',
    name: 'Business rule task',
    type: 'businessRuleTask',
  },
  ScriptTask: {
    id: 'ScriptTask',
    name: 'Script task',
    type: 'scriptTask',
  },
  SendTask: {
    id: 'SendTask',
    name: 'Send task',
    type: 'sendTask',
  },
  IntermediateTimerEvent: {
    id: 'IntermediateTimerEvent',
    name: 'IntermediateTimerEvent',
    type: 'timerIntermediateCatch',
  },
  EventBasedGateway: {
    id: 'EventBasedGatewayTask',
    name: 'EventBasedGateway',
    type: 'eventBasedGateway',
  },
  SignalIntermediateCatch: {
    id: 'SignalIntermediateCatch',
    name: 'Signal intermediate catch',
    type: 'signalIntermediateCatch',
  },
  SignalBoundaryEvent: {
    id: 'SignalBoundaryEvent',
    name: 'Signal boundary event',
    type: 'signalBoundaryEvent',
  },
  SignalEventSubProcess: {
    id: 'SignalEventSubProcess',
    name: 'Signal event sub process',
    type: 'signalEventSubProcess',
  },
  SignalStartEvent: {
    id: 'SignalStartEvent',
    name: 'Signal start event',
    type: 'signalStartEvent',
  },
  ErrorEventSubProcess: {
    id: 'ErrorEventSubProcess',
    name: 'Error event sub process',
    type: 'errorEventSubProcess',
  },
  ErrorStartEvent: {
    id: 'ErrorStartEvent',
    name: 'Error start event',
    type: 'ErrorStartEvent',
  },
  MultiInstanceSubProcess: {
    id: 'MultiInstanceSubProcess',
    name: 'Multi instance sub process',
    type: 'MultiInstanceSubProcess',
  },
  MultiInstanceTask: {
    id: 'MultiInstanceTask',
    name: 'Multi instance task',
    type: 'MultiInstanceTask',
  },
  EscalationEventSubProcess: {
    id: 'EscalationEventSubProcess',
    name: 'Escalation event sub process',
    type: 'EscalationEventSubProcess',
  },
  EscalationStartEvent: {
    id: 'EscalationStartEvent',
    name: 'Escalation start event',
    type: 'EscalationStartEvent',
  },
  CompensationTask: {
    id: 'CompensationTask',
    name: 'Compensation task',
    type: 'CompensationTask',
  },
  CompensationBoundaryEvent: {
    id: 'CompensationBoundaryEvent',
    name: 'Compensation boundary event',
    type: 'CompensationBoundaryEvent',
  },
  ParallelGateway_1: {
    id: 'ParallelGateway_1',
    name: 'ParallelGateway_1',
    type: 'ParallelGateway',
  },
  ParallelGateway_2: {
    id: 'ParallelGateway_1',
    name: 'ParallelGateway_1',
    type: 'ParallelGateway',
  },
  ParallelGateway_3: {
    id: 'ParallelGateway_1',
    name: 'ParallelGateway_1',
    type: 'ParallelGateway',
  },
  ParallelGateway_4: {
    id: 'ParallelGateway_1',
    name: 'ParallelGateway_1',
    type: 'ParallelGateway',
  },
};

type Props = {
  children?: React.ReactNode;
};

const SOURCE_PROCESS_DEFINITION_KEY = '1';

const Wrapper = ({children}: Props) => {
  processInstanceMigrationStore.enable();
  processInstanceMigrationStore.setSourceProcessDefinitionKey(
    SOURCE_PROCESS_DEFINITION_KEY,
  );

  useEffect(() => {
    return () => {
      processInstanceMigrationStore.reset();
      processStatisticsStore.reset();
    };
  }, []);

  return (
    <QueryClientProvider client={getMockQueryClient()}>
      {children}
    </QueryClientProvider>
  );
};

export {elements, Wrapper, SOURCE_PROCESS_DEFINITION_KEY};
