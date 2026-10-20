/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {expect, test} from '../fixtures';
import {countOrders, ORDER_PROCESS} from '../seed/dataset';

test('analyze how a gateway affects reaching an end event', async ({analysisPage}) => {
  const inStock = countOrders((order) => order.inStock);
  const shipped = countOrders((order) => order.inStock && order.outcome === 'completed');

  await analysisPage.gotoBranchAnalysis();
  await analysisPage.selectProcess(ORDER_PROCESS.name);
  await analysisPage.selectFlowNode('inStockGateway');
  await analysisPage.selectFlowNode('orderShipped');

  await expect(analysisPage.result).toContainText(
    `Of all ${countOrders()} instances that passed the gateway ${ORDER_PROCESS.flowNodes.inStockGateway}`
  );
  await expect(
    analysisPage.result.getByRole('listitem').filter({hasText: 'Yes branch'})
  ).toContainText(new RegExp(`^${inStock} \\(.*\\) took the Yes branch, ${shipped} \\(`));
});
