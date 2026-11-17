/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

// Single source of truth for the seeded data. Tests derive every expected number from here.

export type OrderOutcome = 'completed' | 'running' | 'canceled';

export type OrderInstance = {
  version: 1 | 2;
  inStock: boolean;
  amount: number;
  category: 'books' | 'electronics';
  express: boolean;
  outcome: OrderOutcome;
  assignee?: 'demo' | 'john';
};

export const ORDER_PROCESS = {
  key: 'e2e-order-process',
  name: 'Order process',
  resources: ['order-process-v1.bpmn', 'order-process-v2.bpmn'],
  instances: [
    {
      version: 1,
      inStock: true,
      amount: 100,
      category: 'books',
      express: false,
      outcome: 'completed',
      assignee: 'demo',
    },
    {
      version: 1,
      inStock: true,
      amount: 250,
      category: 'books',
      express: true,
      outcome: 'completed',
      assignee: 'demo',
    },
    {
      version: 1,
      inStock: true,
      amount: 80,
      category: 'electronics',
      express: false,
      outcome: 'completed',
      assignee: 'john',
    },
    {
      version: 1,
      inStock: false,
      amount: 40,
      category: 'electronics',
      express: false,
      outcome: 'completed',
    },
    {
      version: 2,
      inStock: true,
      amount: 500,
      category: 'electronics',
      express: true,
      outcome: 'completed',
      assignee: 'john',
    },
    {
      version: 2,
      inStock: false,
      amount: 20,
      category: 'books',
      express: false,
      outcome: 'completed',
    },
    {
      version: 2,
      inStock: true,
      amount: 60,
      category: 'books',
      express: false,
      outcome: 'running',
      assignee: 'demo',
    },
    {
      version: 2,
      inStock: true,
      amount: 75,
      category: 'electronics',
      express: false,
      outcome: 'running',
    },
    {version: 2, inStock: true, amount: 90, category: 'books', express: false, outcome: 'canceled'},
  ] satisfies OrderInstance[] as OrderInstance[],
  flowNodes: {
    checkStock: 'Check stock',
    inStockGateway: 'In stock?',
    approveOrder: 'Approve order',
    notifyCustomer: 'Notify customer',
    orderShipped: 'Order shipped',
    orderRejected: 'Order rejected',
  },
} as const;

export type IncidentInstance = {incident: 'open' | 'resolved'};

export const INCIDENT_PROCESS = {
  key: 'e2e-incident-process',
  name: 'Incident process',
  resources: ['incident-process.bpmn'],
  instances: [
    {incident: 'open'},
    {incident: 'resolved'},
  ] satisfies IncidentInstance[] as IncidentInstance[],
} as const;

export const USERS = {
  demo: {username: 'demo', password: 'demo'},
  john: {username: 'john', password: 'john'},
} as const;

export type UserName = keyof typeof USERS;

export function countOrders(predicate: (instance: OrderInstance) => boolean = () => true): number {
  return ORDER_PROCESS.instances.filter(predicate).length;
}
