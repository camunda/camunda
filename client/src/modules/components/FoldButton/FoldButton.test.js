import React from 'react';
import {shallow} from 'enzyme';

import {LeftBar, RightBar, UpBar, DownBar} from 'modules/components/Icon';

import FoldButton from './FoldButton';

import * as Styled from './styled';

describe('FoldButton', () => {
  it('should render button component', () => {
    const node = shallow(<FoldButton type={'left'} />);
    expect(node.find(Styled.FoldButton)).toExist();
  });

  it('should render LeftBar icon components', () => {
    const node = shallow(<FoldButton type={'left'} />);
    expect(node.find(LeftBar)).toExist();
  });

  it('should render RightBar icon compoenent', () => {
    const node = shallow(<FoldButton type={'right'} />);
    expect(node.find(RightBar)).toExist();
  });

  it('should render UpBar icon component', () => {
    const node = shallow(<FoldButton type={'up'} />);
    expect(node.find(UpBar)).toExist();
  });

  it('should render DownBar icon compoenent', () => {
    const node = shallow(<FoldButton type={'down'} />);
    expect(node.find(DownBar)).toExist();
  });
});
