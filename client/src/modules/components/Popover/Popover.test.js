/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {shallow} from 'enzyme';

import {Button} from 'components';

import Popover from './Popover';

it('should include a button to toggle the popover', () => {
  const node = shallow(<Popover title="Foobar" />);

  expect(node.find(Button)).toExist();
});

it('should render the provided title in the button', () => {
  const node = shallow(<Popover title="Foobar" />);

  expect(node).toIncludeText('Foobar');
});

it('should do not display child content initially', () => {
  const node = shallow(<Popover title="a">Child content</Popover>);

  expect(node).not.toIncludeText('Child content');
});

it('should display child content when the popover is open', async () => {
  const node = shallow(<Popover title="a">Child content</Popover>);

  node.setState({open: true});

  expect(node).toIncludeText('Child content');
});

it('should automatically open the popover on mount with the autoOpen prop', () => {
  const node = shallow(
    <Popover title="a" autoOpen>
      Child content
    </Popover>
  );

  expect(node).toIncludeText('Child content');
});

it('should not display child content if popover is disabled', async () => {
  const node = shallow(
    <Popover disabled={true} title="a">
      Child content
    </Popover>
  );

  node.setState({open: true});

  expect(node).not.toIncludeText('Child content');
});

it('should close the popover when clicking the button again', () => {
  const node = shallow(<Popover title="a">Child content</Popover>);

  node.find(Button).simulate('click', {preventDefault: jest.fn()});
  node.find(Button).simulate('click', {preventDefault: jest.fn()});

  expect(node).not.toIncludeText('Child content');
});

it('should not close the popover when clicking inside the popover', () => {
  const node = shallow(
    <Popover title="a">
      <p>Child content</p>
    </Popover>
  );

  node.setState({open: true});
  node.find('p').simulate('click');

  expect(node).toIncludeText('Child content');
});

it('should display tooltip on button', () => {
  const node = shallow(
    <Popover title="a" tooltip="myTooltip">
      <p>Child content</p>
    </Popover>
  );

  expect(node.find(Button)).toHaveProp('title', 'myTooltip');
});

it('should limit the height and show scrollbar when there is not space', () => {
  const node = shallow(
    <Popover title="a">
      <p>Child content</p>
    </Popover>
  );

  node.instance().buttonRef = {
    getBoundingClientRect: () => ({left: 0, bottom: 0}),
  };

  node.instance().popoverDialogRef = {
    clientWidth: 50,
    clientHeight: 200,
  };

  node.instance().footerRef = {
    getBoundingClientRect: () => ({top: 100}),
  };

  node.instance().calculateDialogStyle();
  node.setState({open: true});
  node.update();
  expect(node.state().dialogStyles.height).toBe('80px');
  expect(node.find('.Popover__dialog')).toHaveClassName('scrollable');
});
