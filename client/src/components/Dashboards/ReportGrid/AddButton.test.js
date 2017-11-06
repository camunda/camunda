import React from 'react';
import {mount} from 'enzyme';

import AddButton from './AddButton';

const showMock = jest.fn();

it('should render button text', async () => {
  const node = mount(<AddButton
    buttonSize={2}
    buttonTop={0}
    buttonLeft={0}
    gridMargin={10}
    gridSize={16}
    onClick={showMock}
  />);

  expect(node).toIncludeText('Add a report');
});

it('should callback on click', async () => {
  const node = mount(<AddButton
    buttonSize={2}
    buttonTop={0}
    buttonLeft={0}
    gridMargin={10}
    gridSize={16}
    onClick={showMock}
  />);

  node.find('.add-button').simulate('click');
  expect(showMock).toHaveBeenCalled();
});



