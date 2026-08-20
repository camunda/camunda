/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {shallow} from 'enzyme';

import KpiDeltaBadge from './KpiDeltaBadge';

describe('KpiDeltaBadge', () => {
  it('should show grey badge when priorValue is null', () => {
    const wrapper = shallow(
      <KpiDeltaBadge
        currentValue={100}
        priorValue={null}
        unit=""
        deltaGoodDirection="up"
        periodLabel="WoW"
      />
    );
    expect(wrapper.prop('type')).toBe('gray');
    expect(wrapper.text()).toContain('— WoW');
  });

  it('should show grey badge when priorValue is undefined', () => {
    const wrapper = shallow(
      <KpiDeltaBadge
        currentValue={100}
        priorValue={undefined}
        unit=""
        deltaGoodDirection="up"
        periodLabel="WoW"
      />
    );
    expect(wrapper.prop('type')).toBe('gray');
    expect(wrapper.text()).toContain('— WoW');
  });

  it('should show green badge when positive delta and direction is up', () => {
    const wrapper = shallow(
      <KpiDeltaBadge
        currentValue={230}
        priorValue={100}
        unit=""
        deltaGoodDirection="up"
        periodLabel="WoW"
      />
    );
    expect(wrapper.prop('type')).toBe('green');
    expect(wrapper.text()).toContain('+130');
  });

  it('should show red badge when negative delta and direction is up', () => {
    const wrapper = shallow(
      <KpiDeltaBadge
        currentValue={80}
        priorValue={100}
        unit=""
        deltaGoodDirection="up"
        periodLabel="WoW"
      />
    );
    expect(wrapper.prop('type')).toBe('red');
    expect(wrapper.text()).toContain('-20');
  });

  it('should show green badge when negative delta and direction is down', () => {
    const wrapper = shallow(
      <KpiDeltaBadge
        currentValue={3500}
        priorValue={4000}
        unit="ms"
        deltaGoodDirection="down"
        periodLabel="WoW"
      />
    );
    expect(wrapper.prop('type')).toBe('green');
  });

  it('should show red badge when positive delta and direction is down', () => {
    const wrapper = shallow(
      <KpiDeltaBadge
        currentValue={4500}
        priorValue={4000}
        unit="ms"
        deltaGoodDirection="down"
        periodLabel="WoW"
      />
    );
    expect(wrapper.prop('type')).toBe('red');
  });

  it('should format duration in seconds when delta >= 1000ms', () => {
    const wrapper = shallow(
      <KpiDeltaBadge
        currentValue={3500}
        priorValue={5000}
        unit="ms"
        deltaGoodDirection="down"
        periodLabel="WoW"
      />
    );
    expect(wrapper.text()).toContain('-1.5s');
  });

  it('should format duration in milliseconds when delta < 1000ms', () => {
    const wrapper = shallow(
      <KpiDeltaBadge
        currentValue={400}
        priorValue={450}
        unit="ms"
        deltaGoodDirection="down"
        periodLabel="WoW"
      />
    );
    expect(wrapper.text()).toContain('-50ms');
  });

  it('should format percentage with one decimal when < 1%', () => {
    const wrapper = shallow(
      <KpiDeltaBadge
        currentValue={0.15}
        priorValue={0.25}
        unit="%"
        deltaGoodDirection="down"
        periodLabel="WoW"
      />
    );
    expect(wrapper.text()).toContain('-0.1%');
  });

  it('should format percentage with no decimal when >= 1%', () => {
    const wrapper = shallow(
      <KpiDeltaBadge
        currentValue={5}
        priorValue={3}
        unit="%"
        deltaGoodDirection="up"
        periodLabel="WoW"
      />
    );
    expect(wrapper.text()).toContain('+2%');
  });

  it('should show grey badge when delta is zero', () => {
    const wrapper = shallow(
      <KpiDeltaBadge
        currentValue={100}
        priorValue={100}
        unit=""
        deltaGoodDirection="up"
        periodLabel="WoW"
      />
    );
    expect(wrapper.prop('type')).toBe('gray');
    expect(wrapper.text()).toContain('+0');
  });

  it('should append WoW suffix', () => {
    const wrapper = shallow(
      <KpiDeltaBadge
        currentValue={130}
        priorValue={0}
        unit=""
        deltaGoodDirection="up"
        periodLabel="WoW"
      />
    );
    expect(wrapper.text()).toContain('WoW');
  });
});
