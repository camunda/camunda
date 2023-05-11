/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {cleanEntities} from '../setup';
import config from '../config';
import * as u from '../utils';

import * as Report from './DecisionReport.elements.js';
import * as ProcessReport from './ProcessReport.elements.js';
import * as Filter from './Filter.elements.js';
import * as Common from './Common.elements.js';

fixture('Decision Report Filter')
  .page(config.endpoint)
  .beforeEach(u.login)
  .afterEach(cleanEntities);

test('should apply a filter to the report result', async (t) => {
  await u.createNewDecisionReport(t);

  await u.selectDefinition(t, 'Invoice Classification');
  await u.selectView(t, 'Evaluation Count');

  const unfiltered = +(await ProcessReport.reportNumber.textContent);

  await t.click(Report.sectionToggle('Filters'));
  await t.click(Report.filterButton);
  await t.click(Report.filterOption('Input Variable'));
  await t.click(Common.typeahead);
  await t.click(Common.typeaheadOption('Invoice Amount'));
  await t.click(Filter.variableFilterOperatorButton('is less than'));

  await t.typeText(Filter.variableFilterValueInput, '100', {replace: true});

  await t.click(Common.carbonModalConfirmBtn);

  const filtered = +(await ProcessReport.reportNumber.textContent);

  await t.expect(unfiltered).gt(filtered);
});

test('should have seperate input and output variables', async (t) => {
  await u.createNewDecisionReport(t);

  await u.selectDefinition(t, 'Invoice Classification');
  await u.selectView(t, 'Evaluation Count');

  await t.click(Report.sectionToggle('Filters'));
  await t.click(Report.filterButton);
  await t.click(Report.filterOption('Input Variable'));
  await t.click(Common.typeahead);
  await t.expect(Common.typeahead.textContent).notContains('Classification');
  await t.click(Filter.modalCancel);

  await t.click(Report.filterButton);
  await t.click(Report.filterOption('Output Variable'));
  await t.click(Common.typeahead);

  await t.expect(Common.typeahead.textContent).notContains('Invoice Amount');
  await t.expect(Common.typeahead.textContent).contains('Classification');
});
