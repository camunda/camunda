/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {mount} from 'enzyme';

import ProgressBar from './ProgressBar';

describe('ProgressBar', () => {
  it('should render 0% progress (count: 0)', () => {
    // when
    const node = mount(<ProgressBar totalCount={0} finishedCount={0} />);

    // then
    expect(node.find('[data-test="progress-bar"]')).toHaveStyleRule(
      'width',
      '0%'
    );
  });

  it('should render 0% progress (count: 5)', () => {
    // when
    const node = mount(<ProgressBar totalCount={5} finishedCount={0} />);

    // then
    expect(node.find('[data-test="progress-bar"]')).toHaveStyleRule(
      'width',
      '0%'
    );
  });

  it('should render 33% progress', () => {
    // when
    const node = mount(<ProgressBar totalCount={9} finishedCount={3} />);

    // then
    expect(node.find('[data-test="progress-bar"]')).toHaveStyleRule(
      'width',
      '33%'
    );
  });

  it('should render 100% progress', () => {
    // when
    const node = mount(<ProgressBar totalCount={5} finishedCount={5} />);

    // then
    expect(node.find('[data-test="progress-bar"]')).toHaveStyleRule(
      'width',
      '100%'
    );
  });
});
