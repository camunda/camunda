/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {shallow} from 'enzyme';

import {useDocs} from 'hooks';

import withDocs from './withDocs';

jest.mock('hooks', () => ({
  useDocs: jest.fn().mockImplementation(() => ({
    generateDocsLink: jest.fn(),
    getBaseDocsUrl: jest.fn(),
  })),
}));

it('should call useDocs on a component creation', () => {
  const Component = withDocs(function () {
    return <div></div>;
  });

  shallow(<Component />);

  expect(useDocs).toHaveBeenCalled();
});

it('should pass the props from useDocs into the wrapped component', () => {
  const Component = withDocs(function () {
    return <div></div>;
  });

  const initialProps = {
    a: 'a',
    b: 'b',
  };

  const node = shallow(<Component {...initialProps} />);

  expect(node.props()).toEqual({
    ...initialProps,
    generateDocsLink: expect.any(Function),
    getBaseDocsUrl: expect.any(Function),
  });
});
