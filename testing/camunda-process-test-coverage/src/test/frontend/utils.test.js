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

import {
  toPercent,
  coverageClass,
  escapeHtml,
  progressBarHtml,
  badgeHtml,
  statCard,
  processLabel,
  decisionLabel,
  runPrimaryLabel,
  runSecondaryLabel,
} from '../../main/frontend/utils.js';

describe('toPercent', () => {
  test('converts 0 to 0.0%', () => {
    expect(toPercent(0)).toBe('0.0%');
  });

  test('converts 1 to 100.0%', () => {
    expect(toPercent(1)).toBe('100.0%');
  });

  test('converts 0.75 to 75.0%', () => {
    expect(toPercent(0.75)).toBe('75.0%');
  });

  test('converts 0.333 to 33.3%', () => {
    expect(toPercent(0.333)).toBe('33.3%');
  });
});

describe('coverageClass', () => {
  test('returns coverage-high for value >= 0.8', () => {
    expect(coverageClass(1.0)).toBe('coverage-high');
    expect(coverageClass(0.8)).toBe('coverage-high');
  });

  test('returns coverage-medium for value >= 0.5 and < 0.8', () => {
    expect(coverageClass(0.5)).toBe('coverage-medium');
    expect(coverageClass(0.79)).toBe('coverage-medium');
  });

  test('returns coverage-low for value < 0.5', () => {
    expect(coverageClass(0.0)).toBe('coverage-low');
    expect(coverageClass(0.49)).toBe('coverage-low');
  });
});

describe('escapeHtml', () => {
  test('escapes ampersands', () => {
    expect(escapeHtml('a & b')).toBe('a &amp; b');
  });

  test('escapes less-than', () => {
    expect(escapeHtml('<script>')).toBe('&lt;script&gt;');
  });

  test('escapes double-quotes', () => {
    expect(escapeHtml('"value"')).toBe('&quot;value&quot;');
  });

  test('leaves safe strings unchanged', () => {
    expect(escapeHtml('hello world')).toBe('hello world');
  });
});

describe('progressBarHtml', () => {
  test('includes the percentage value', () => {
    const html = progressBarHtml(0.6);
    expect(html).toContain('60.0%');
  });

  test('applies the correct coverage class', () => {
    expect(progressBarHtml(0.9)).toContain('coverage-high');
    expect(progressBarHtml(0.6)).toContain('coverage-medium');
    expect(progressBarHtml(0.3)).toContain('coverage-low');
  });

  test('produces a div element', () => {
    const html = progressBarHtml(0.5);
    expect(html).toContain('<div');
  });
});

describe('badgeHtml', () => {
  test('includes the percentage value', () => {
    const html = badgeHtml(0.75);
    expect(html).toContain('75.0%');
  });

  test('applies the correct coverage class', () => {
    expect(badgeHtml(1.0)).toContain('coverage-high');
    expect(badgeHtml(0.6)).toContain('coverage-medium');
    expect(badgeHtml(0.1)).toContain('coverage-low');
  });

  test('produces a span element', () => {
    const html = badgeHtml(0.5);
    expect(html).toContain('<span');
  });
});

describe('statCard', () => {
  test('includes the value and label', () => {
    const html = statCard(42, 'Test Cases', 'bi-file', null);
    expect(html).toContain('42');
    expect(html).toContain('Test Cases');
  });

  test('applies the extra class when provided', () => {
    const html = statCard('75.0%', 'Avg. Coverage', 'bi-bar-chart', 'coverage-high');
    expect(html).toContain('coverage-high');
  });

  test('uses default column class when not specified', () => {
    const html = statCard(1, 'Suites', 'bi-folder', null);
    expect(html).toContain('col-sm-6');
  });

  test('uses the provided column class', () => {
    const html = statCard(1, 'Suites', 'bi-folder', null, 'col');
    expect(html).toContain('class="col"');
  });
});

describe('processLabel', () => {
  const models = [
    { processDefinitionId: 'proc-a', processName: 'Process A' },
    { processDefinitionId: 'proc-b', processName: null },
  ];

  test('returns processName when available', () => {
    expect(processLabel('proc-a', models)).toBe('Process A');
  });

  test('falls back to processDefinitionId when name is null', () => {
    expect(processLabel('proc-b', models)).toBe('proc-b');
  });

  test('falls back to processDefinitionId when model is not found', () => {
    expect(processLabel('proc-c', models)).toBe('proc-c');
  });

  test('handles null models gracefully', () => {
    expect(processLabel('proc-a', null)).toBe('proc-a');
  });
});

describe('decisionLabel', () => {
  const models = [
    { decisionDefinitionId: 'dec-a', decisionName: 'Decision A' },
    { decisionDefinitionId: 'dec-b', decisionName: null },
  ];

  test('returns decisionName when available', () => {
    expect(decisionLabel('dec-a', models)).toBe('Decision A');
  });

  test('falls back to decisionDefinitionId when name is null', () => {
    expect(decisionLabel('dec-b', models)).toBe('dec-b');
  });

  test('falls back to decisionDefinitionId when model is not found', () => {
    expect(decisionLabel('dec-c', models)).toBe('dec-c');
  });
});

describe('runPrimaryLabel', () => {
  test('returns displayName when set', () => {
    expect(runPrimaryLabel({ name: 'shouldFoo', displayName: 'When foo is called' })).toBe(
      'When foo is called'
    );
  });

  test('returns name when displayName is null', () => {
    expect(runPrimaryLabel({ name: 'shouldFoo', displayName: null })).toBe('shouldFoo');
  });

  test('returns name when displayName is undefined', () => {
    expect(runPrimaryLabel({ name: 'shouldFoo' })).toBe('shouldFoo');
  });
});

describe('runSecondaryLabel', () => {
  test('returns method name when display name is set and different', () => {
    expect(
      runSecondaryLabel({ name: 'shouldFoo', displayName: 'When foo is called' })
    ).toBe('shouldFoo');
  });

  test('returns null when display name equals method name', () => {
    expect(runSecondaryLabel({ name: 'shouldFoo', displayName: 'shouldFoo' })).toBeNull();
  });

  test('returns null when display name is not set', () => {
    expect(runSecondaryLabel({ name: 'shouldFoo', displayName: null })).toBeNull();
  });
});
