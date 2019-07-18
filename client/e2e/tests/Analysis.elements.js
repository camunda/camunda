/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {Selector} from 'testcafe';

export const navItem = Selector('header a').withText('Analysis');
export const flowNode = id => Selector(`[data-element-id="${id}"]`);
export const statisticsDiagram = Selector('.Statistics canvas');
export const endEventOverlay = Selector('.DiagramBehavior__end-event-statistics');
export const gatewayCancelButton = Selector('[name="AnalysisControlPanel__gateway"] button');
export const gatewayInput = Selector('[name="AnalysisControlPanel__gateway"] .content');
export const endEventInput = Selector('[name="AnalysisControlPanel__endEvent"] .content');
