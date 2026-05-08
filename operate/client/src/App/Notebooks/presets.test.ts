/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {pickConfigs} from './presets';

// presets now always returns *thematically related* bundles (not single
// widgets) so the same prompt feels generative across runs. Tests therefore
// assert on theme/contents rather than exact length-1.

describe('presets.pickConfigs', () => {
  it('should return a multi-widget Monday morning bundle', () => {
    // when
    const configs = pickConfigs('Set me up with a Monday morning view');

    // then
    expect(configs.length).toBeGreaterThan(1);
    // Monday morning bundle always contains a BPMN heatmap of order-process
    expect(configs.some((c) => c.type === 'bpmn')).toBe(true);
  });

  it('should produce an incident-themed bundle for "list all incidents"', () => {
    // when
    const configs = pickConfigs('list all incidents');

    // then – every widget should target the incidents domain
    expect(configs.length).toBeGreaterThan(0);
    const allIncidentDomain = configs.every(
      (c) =>
        c.query.endpoint.includes('incidents') ||
        c.type === 'bpmn' ||
        c.title.toLowerCase().includes('incident'),
    );
    expect(allIncidentDomain).toBe(true);
  });

  it('should produce an incident-themed bundle when prompt mentions incidents generically', () => {
    // when
    const configs = pickConfigs('how is incidents looking');

    // then
    expect(configs.length).toBeGreaterThan(0);
    const someIncidentDomain = configs.some(
      (c) =>
        c.query.endpoint.includes('incidents') ||
        c.type === 'bpmn' ||
        c.title.toLowerCase().includes('incident'),
    );
    expect(someIncidentDomain).toBe(true);
  });

  it('should return a multi-widget overview as the fallback for unmatched prompts', () => {
    // given – a prompt that matches none of the keyword patterns
    // when
    const configs = pickConfigs('completely unrelated text xyzzy');

    // then – fallback gives a meaningful overview, not a lonely metric
    expect(configs.length).toBeGreaterThan(1);
    expect(configs.some((c) => c.type === 'metric')).toBe(true);
  });

  it('should return a single metric for "how many" prompts', () => {
    // when
    const configs = pickConfigs('how many active process instances');

    // then – count prompts intentionally still yield one metric
    expect(configs).toHaveLength(1);
    expect(configs[0]?.type).toBe('metric');
  });

  it('should produce a jobs-themed bundle for "show me jobs"', () => {
    // when
    const configs = pickConfigs('show me jobs');

    // then – at least one widget should target the jobs domain
    expect(configs.length).toBeGreaterThan(0);
    const someJobsDomain = configs.some(
      (c) =>
        c.query.endpoint.includes('jobs') ||
        c.title.toLowerCase().includes('job'),
    );
    expect(someJobsDomain).toBe(true);
  });

  it('should route "What error types are happening most?" to the error preset (not the activity preset)', () => {
    // given – this is the "Top errors" suggestion pill prompt. Earlier the
    // /what.*happen/ matcher swallowed it before the error-specific matcher
    // could run, producing recent instances instead of incidents.
    // when
    const configs = pickConfigs('What error types are happening most?');

    // then – every widget should target the incidents/errors domain.
    expect(configs.length).toBeGreaterThan(0);
    const everyOneIsErrorThemed = configs.every(
      (c) =>
        c.query.endpoint.includes('incidents') ||
        c.title.toLowerCase().includes('incident') ||
        c.title.toLowerCase().includes('error'),
    );
    expect(everyOneIsErrorThemed).toBe(true);
  });
});
