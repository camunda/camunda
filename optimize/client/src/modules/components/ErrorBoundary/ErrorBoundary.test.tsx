/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import React from 'react';
import {mount} from 'enzyme';

import ErrorBoundary from './ErrorBoundary';

// disable console error logging for this file
console.error = jest.fn();

it('renders child components normally', () => {
  const NormalComponent = () => <div>I am perfectly fine</div>;

  const node = mount(
    <ErrorBoundary>
      <NormalComponent />
    </ErrorBoundary>
  );

  expect(node).toIncludeText('I am perfectly fine');
});

it('displays the error message the child throws', () => {
  const LyingCompoent = () => (
    <div>
      I am perfectly fine
      {(() => {
        throw new Error('Some error');
      })()}
    </div>
  );

  const node = mount(
    <ErrorBoundary>
      <LyingCompoent />
    </ErrorBoundary>
  );

  expect(node).not.toIncludeText('I am perfectly fine');
  expect(node).toIncludeText('Some error');
});
