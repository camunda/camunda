/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import setup from '../setup';
import config from '../config';
import * as u from '../utils';

import * as Analysis from './Analysis.elements.js';

fixture('Process Analysis')
  .page(config.endpoint)
  .before(setup)
  .beforeEach(u.login);

test('show the statistics diagram', async t => {
  await t.click(Analysis.navItem);

  await u.selectDefinition(t, 'Lead Qualification');

  await t.click(Analysis.flowNode('ExclusiveGateway_0rta6cr'));
  await t.click(Analysis.flowNode('msLeadIsOpp'));

  await t.expect(Analysis.statisticsDiagram.visible).ok();
});

test('show end event statistics on hover', async t => {
  await t.click(Analysis.navItem);

  await u.selectDefinition(t, 'Lead Qualification');

  await t.hover(Analysis.flowNode('msLeadIsOpp'));

  await t.expect(Analysis.endEventOverlay.visible).ok();
  await t.expect(Analysis.endEventOverlay.textContent).contains('Process Instances Total');
  await t
    .expect(Analysis.endEventOverlay.textContent)
    .contains('Process Instances reached this state');
  await t
    .expect(Analysis.endEventOverlay.textContent)
    .contains('of Process Instances reached this state');
});

test('should deselect elements by clicking on the node or on the control panel', async t => {
  await t.click(Analysis.navItem);

  await u.selectDefinition(t, 'Lead Qualification');

  await t.click(Analysis.flowNode('ExclusiveGateway_0rta6cr'));
  await t.click(Analysis.flowNode('msLeadIsOpp'));

  await t.expect(Analysis.gatewayInput.textContent).eql('Call them right away?');
  await t.expect(Analysis.endEventInput.textContent).eql('Lead is Opp');

  await t.click(Analysis.flowNode('msLeadIsOpp'));

  await t.expect(Analysis.endEventInput.textContent).eql('Select End Event');

  await t.click(Analysis.gatewayCancelButton);

  await t.expect(Analysis.gatewayInput.textContent).eql('Select Gateway');
});
