/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {cleanEntities} from '../setup';
import config from '../config';
import * as u from '../utils';
import {addAnnotation, clearAllAnnotations} from '../browserMagic';

import * as Analysis from './Analysis.elements.js';
import * as Common from './Common.elements.js';

fixture('Process Analysis')
  .page(config.endpoint + '#/analysis')
  .beforeEach(u.login)
  .afterEach(cleanEntities);

test('show the statistics diagram', async (t) => {
  await t.click(Analysis.branchAnalysisLink);

  await t.resizeWindow(1600, 750);

  await u.selectDefinition(t, 'Book Request with no business key');

  await t.click(Analysis.flowNode('ExclusiveGateway_0i9u2oe'));
  await t.click(Analysis.flowNode('EndEvent_0kcx8gn'));

  await t.expect(Analysis.statisticsDiagram.visible).ok();

  await t.takeScreenshot('process-analysis/img/analysis-2.png', {fullPage: true}).maximizeWindow();
});

test('show end event statistics on hover', async (t) => {
  await t.click(Analysis.branchAnalysisLink);

  await t.resizeWindow(1600, 700);

  await u.selectDefinition(t, 'Book Request with no business key');

  await t.hover(Analysis.flowNode('EndEvent_0kcx8gn'));

  await t.expect(Analysis.endEventOverlay.visible).ok();
  await t.expect(Analysis.endEventOverlay.textContent).contains('Process Instances Total');
  await t
    .expect(Analysis.endEventOverlay.textContent)
    .contains('Process Instances reached this state');
  await t
    .expect(Analysis.endEventOverlay.textContent)
    .contains('of Process Instances reached this state');

  await addAnnotation(Analysis.endEventOverlay, 'End Event Information', {x: 50, y: 0});

  await t.takeScreenshot('process-analysis/img/analysis-1.png', {fullPage: true}).maximizeWindow();

  await clearAllAnnotations();
});

test('should deselect elements by clicking on the node or on the control panel', async (t) => {
  await t.click(Analysis.branchAnalysisLink);

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

test('should show outliers heatmap when selecting a process definition', async (t) => {
  await t.resizeWindow(1050, 700);

  await u.selectDefinition(t, 'Analysis Testing Process', [6, 5, 4, 3]);
  await t.hover(Analysis.flowNode('AE0010P0030'));

  await t
    .takeScreenshot('process-analysis/img/outlierExample_1_heatMap.png', {
      fullPage: true,
    })
    .maximizeWindow();

  await t.expect(Analysis.heatmapEl.visible).ok();
});

test('should show outlier details modal when clicking view details on a flow node', async (t) => {
  await u.selectDefinition(t, 'Analysis Testing Process', [6, 5, 4, 3]);

  await t.hover(Analysis.flowNode('AE0010P0030'));

  await t.click(Analysis.tooltipDetailsButton);

  await t
    .resizeWindow(1600, 800)
    .takeElementScreenshot(
      Common.modalContainer,
      'process-analysis/img/outlierExample_2_distribution.png'
    )
    .maximizeWindow();

  await t.expect(Analysis.chart.visible).ok();
});

test('should show common outliers variables as a table', async (t) => {
  await u.selectDefinition(t, 'Analysis Testing Process', [6, 5, 4, 3]);

  await t.hover(Analysis.flowNode('AE0010P0030'));

  await t.click(Analysis.tooltipDetailsButton);

  await t.click(Analysis.commonVariablesButton);

  await t
    .resizeWindow(1600, 800)
    .takeElementScreenshot(
      Common.modalContainer,
      'process-analysis/img/outlierExample_3_Variables.png'
    )
    .maximizeWindow();

  await t.expect(Analysis.variablesTable.visible).ok();
});
