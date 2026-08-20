/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {shallow} from 'enzyme';

import {useErrorHandling} from 'hooks';

import withErrorHandling from './withErrorHandling';

jest.mock('hooks', () => ({
  useErrorHandling: jest
    .fn()
    .mockImplementation(() => ({error: 'error', mightFail: jest.fn(), resetError: jest.fn()})),
}));

it('should call useErrorHandling on a component creation', () => {
  const Component = withErrorHandling(function () {
    return <div></div>;
  });

  shallow(<Component />);

  expect(useErrorHandling).toHaveBeenCalled();
});

it('should pass the props from useErrorHandling into the wrapped component', () => {
  const Component = withErrorHandling(function () {
    return <div></div>;
  });

  const initialProps = {
    a: 'a',
    b: 'b',
  };

  const node = shallow(<Component {...initialProps} />);

  expect(node.props()).toEqual({
    ...initialProps,
    mightFail: expect.any(Function),
    resetError: expect.any(Function),
    error: 'error',
  });
});
