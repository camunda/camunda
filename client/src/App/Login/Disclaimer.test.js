/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {shallow} from 'enzyme';

import Disclaimer from './Disclaimer';
import * as Styled from './styled';

describe('Disclaimer', () => {
  it('should render by default', () => {
    const node = shallow(<Disclaimer />);

    expect(node.find(Styled.Disclaimer)).toHaveLength(1);
  });

  it('should render if not enterprise', () => {
    const node = shallow(<Disclaimer isEnterprise={false} />);

    expect(node.find(Styled.Disclaimer)).toHaveLength(1);
  });

  it('should not render if enterprise', () => {
    const node = shallow(<Disclaimer isEnterprise={true} />);

    expect(node).toBeEmptyRender();
  });
});
