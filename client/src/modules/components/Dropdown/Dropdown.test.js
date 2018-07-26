import React from 'react';
import {shallow} from 'enzyme';
import {mockResolvedAsyncFn, flushPromises} from 'modules/testUtils';

import Dropdown from './Dropdown';

import * as Styled from './styled';

describe('Dropdown', () => {
  let Child, node;
  beforeEach(() => {
    Child = () => <span>I am a child component</span>;
    node = shallow(
      <Dropdown label="Foo">
        <Child />
      </Dropdown>
    );
    node.instance().storeContainer(document.createElement('div'));
  });

  it('should be closed initially', () => {});

  it('should not display its child contents when it is closed', () => {
    expect(node.find(Child)).not.toExist();
  });

  it('should open on click of the label', () => {
    node.find('[data-test-id="dropdown-label"]').simulate('click');

    expect(node.state().open).toBe(true);
  });

  it('should display its child contents if it is open', () => {
    node.setState({open: true});

    expect(node.find(Child)).toExist();
  });

  it('should close the dropdown when clicking anywhere', () => {
    node.setState({open: true});
    document.body.click();
    expect(node.state().open).toBe(false);
  });

  it('should match snapshot', () => {
    expect(node).toMatchSnapshot();
  });
});

describe('DropdownMenu', () => {
  let node, Child, placement;
  beforeEach(() => {
    Child = () => <span>I am a child component</span>;
    node = shallow(
      <Styled.DropdownMenu placement={'top'}>
        <Child />
      </Styled.DropdownMenu>
    );
  });

  it('should render bottom as default placement direction', async () => {
    //
  });

  it('should pass prop "placement" to children', () => {
    //
  });
});

describe('Dropdown.Option', () => {
  let Child, node, spy;
  beforeEach(() => {
    Child = () => <span>I am a child component</span>;
    spy = jest.fn();
    node = shallow(
      <Dropdown.Option onClick={spy}>
        <Child />
      </Dropdown.Option>
    );
  });

  it('should renders its children', () => {
    expect(node.find(Child)).toExist();
  });

  it('should pass properties', () => {
    node.simulate('click');
    expect(spy).toHaveBeenCalled();
  });
});
