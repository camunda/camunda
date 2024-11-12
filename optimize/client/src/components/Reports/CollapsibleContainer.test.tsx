/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {shallow} from 'enzyme';

import {CollapsibleContainer} from './CollapsibleContainer';

const props = {
  title: 'title',
  maxHeight: 100,
};

it('should render correctly', () => {
  const node = shallow(<CollapsibleContainer {...props}>test</CollapsibleContainer>);

  expect(node.find('b').text()).toContain('title');
  expect(node.childAt(1).text()).toContain('test');
  expect(node.find('.expandButton')).toExist();
  expect(node.find('.collapseButton')).toExist();
});

it('should hide expandButton when section is fully expanded', () => {
  const node = shallow(<CollapsibleContainer {...props} />);

  node.find('.expandButton').simulate('click');
  expect(node.find('.expandButton')).not.toExist();
});

it('should hide collapseButton when section is fully collapsed', () => {
  const node = shallow(<CollapsibleContainer {...props} />);

  node.find('.collapseButton').simulate('click');
  expect(node.find('.collapseButton')).not.toExist();
});

it('should call onExpand when section is expanded', () => {
  const onExpand = jest.fn();
  const node = shallow(<CollapsibleContainer {...props} onExpand={onExpand} />);

  node.find('.expandButton').simulate('click');
  expect(onExpand).toHaveBeenCalled();
});

it('should call onCollapse when section is collapsed', () => {
  const onCollapse = jest.fn();
  const node = shallow(<CollapsibleContainer {...props} onCollapse={onCollapse} />);

  node.find('.collapseButton').simulate('click');
  expect(onCollapse).toHaveBeenCalled();
});

it('should call onTransitionEnd when section is expanded', () => {
  const onTransitionEnd = jest.fn();
  const node = shallow(<CollapsibleContainer {...props} onTransitionEnd={onTransitionEnd} />);

  node.simulate('transitionend', {propertyName: 'max-height'});

  node.find('.expandButton').simulate('click');

  expect(onTransitionEnd).toHaveBeenCalled();
});

it('should not render children when section is collapsed', () => {
  const node = shallow(
    <CollapsibleContainer {...props}>
      <p>child</p>
    </CollapsibleContainer>
  );

  expect(node.find('p')).toExist();

  node.find('.collapseButton').simulate('click');
  node.simulate('transitionend', {propertyName: 'max-height'});

  expect(node.find('p')).not.toExist();
});
