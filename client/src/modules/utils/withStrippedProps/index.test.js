/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {shallow} from 'enzyme';
import withStrippedProps from './index';

describe('withStrippedProps', () => {
  it('remove given props from a component', () => {
    // given
    const testProps = {
      onClick: () => {},
      test: 'blue',
      className: 'main',
    };

    // when
    const Component = (props) => <div {...props} />;
    const StrippedComponent = withStrippedProps(['test', 'onClick'])(Component);
    const node = shallow(<StrippedComponent {...testProps} />);

    // then
    expect(node.props().test).toBe(undefined);
    expect(node.props().onClick).toBe(undefined);
  });
});
