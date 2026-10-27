/*
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

'use strict';

import { renderDashboard } from '../../../main/frontend/views/dashboard.js';

// jsdom provides a browser-like document object; the test environment is configured
// in jest.config.cjs to use jest-environment-jsdom.

describe('renderDashboard', () => {
  let contentDiv;

  beforeEach(() => {
    // Provide the DOM elements expected by renderDashboard
    document.body.innerHTML = '<div id="content"></div>';
    contentDiv = document.getElementById('content');
  });

  test('renders the dashboard heading', () => {
    renderDashboard({ suites: [], processCoverages: [], decisionCoverages: [] });
    expect(contentDiv.innerHTML).toContain('Coverage Dashboard');
  });

  test('renders stat cards for empty data', () => {
    renderDashboard({ suites: [], processCoverages: [], decisionCoverages: [] });
    expect(contentDiv.innerHTML).toContain('Test Suites');
    expect(contentDiv.innerHTML).toContain('Test Cases');
    expect(contentDiv.innerHTML).toContain('Processes');
    expect(contentDiv.innerHTML).toContain('Decisions');
  });

  test('shows process coverage rows when processes are present', () => {
    const data = {
      suites: [],
      processCoverages: [{ processDefinitionId: 'my-process', coverage: 0.75 }],
      decisionCoverages: [],
      processModels: [{ processDefinitionId: 'my-process', processName: 'My Process' }],
      decisionModels: [],
    };

    renderDashboard(data);

    expect(contentDiv.innerHTML).toContain('my-process');
    expect(contentDiv.innerHTML).toContain('My Process');
    expect(contentDiv.innerHTML).toContain('75.0%');
  });

  test('shows decision coverage rows when decisions are present', () => {
    const data = {
      suites: [],
      processCoverages: [],
      decisionCoverages: [{ decisionDefinitionId: 'my-decision', coverage: 0.5 }],
      processModels: [],
      decisionModels: [{ decisionDefinitionId: 'my-decision', decisionName: 'My Decision' }],
    };

    renderDashboard(data);

    expect(contentDiv.innerHTML).toContain('my-decision');
    expect(contentDiv.innerHTML).toContain('My Decision');
    expect(contentDiv.innerHTML).toContain('50.0%');
  });

  test('shows suite rows when suites are present', () => {
    const data = {
      suites: [
        {
          id: 'suite-1',
          name: 'My Test Suite',
          runs: [{ name: 'run-1' }, { name: 'run-2' }],
          processCoverages: [{ processDefinitionId: 'p', coverage: 0.8 }],
          decisionCoverages: [],
        },
      ],
      processCoverages: [],
      decisionCoverages: [],
    };

    renderDashboard(data);

    expect(contentDiv.innerHTML).toContain('My Test Suite');
  });

  test('shows a message when no process coverage data is available', () => {
    renderDashboard({ suites: [], processCoverages: [], decisionCoverages: [] });
    expect(contentDiv.innerHTML).toContain('No process coverage data available');
  });

  test('shows a message when no decision coverage data is available', () => {
    renderDashboard({ suites: [], processCoverages: [], decisionCoverages: [] });
    expect(contentDiv.innerHTML).toContain('No decision coverage data available');
  });

  test('escapes HTML in process definition IDs to prevent XSS', () => {
    const data = {
      suites: [],
      processCoverages: [{ processDefinitionId: '<script>alert(1)</script>', coverage: 0.5 }],
      decisionCoverages: [],
      processModels: [],
      decisionModels: [],
    };

    renderDashboard(data);

    // The raw script tag must NOT appear in the DOM
    expect(contentDiv.innerHTML).not.toContain('<script>alert(1)</script>');
    // The escaped version must appear instead
    expect(contentDiv.innerHTML).toContain('&lt;script&gt;');
  });

  test('renders average coverage in stat card', () => {
    const data = {
      suites: [],
      processCoverages: [{ processDefinitionId: 'p', coverage: 1.0 }],
      decisionCoverages: [{ decisionDefinitionId: 'd', coverage: 0.0 }],
      processModels: [],
      decisionModels: [],
    };

    renderDashboard(data);

    // Average of 100% and 0% = 50.0%
    expect(contentDiv.innerHTML).toContain('50.0%');
  });

  test('handles null or missing optional fields gracefully', () => {
    // No processModels / decisionModels supplied
    const data = {
      suites: [],
      processCoverages: [{ processDefinitionId: 'p-no-model', coverage: 0.3 }],
      decisionCoverages: [],
    };

    expect(() => renderDashboard(data)).not.toThrow();
    expect(contentDiv.innerHTML).toContain('p-no-model');
  });
});
