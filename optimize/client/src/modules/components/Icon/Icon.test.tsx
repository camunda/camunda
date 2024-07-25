/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {mount} from 'enzyme';

import Icon from './Icon';

jest.mock('./icons', () => {
  return {
    plus: (props: React.SVGProps<SVGSVGElement>) => <svg {...props} />,
  };
});

it('should render without crashing', () => {
  mount(<Icon type="plus" />);
});

it('should render a tag as provided as a property when using a background image', () => {
  const node = mount(<Icon type="plus" renderedIn="i" />);

  expect(node.find('.Icon')).toHaveDisplayName('i');
});

it('should render an inline SVG', () => {
  const node = mount(<Icon type="plus" />);

  expect(node.find('svg')).toExist();
});

it('should render an element with a class when "renderedIn" was provided as a property', () => {
  const node = mount(<Icon renderedIn="i" type="plus" />);

  expect(node.find('.Icon')).toMatchSelector('.Icon--plus');
});

it('should render an icon provided as child content', () => {
  const node = mount(<Icon>I am a custom Icon</Icon>);

  expect(node).toIncludeText('I am a custom Icon');
});

it('should be possible to provide a classname to the Icon', () => {
  const node = mount(<Icon type="plus" className="customClassname" />);

  expect(node.find('.customClassname')).toExist();
});

it('should be possible to provide a size to the Icon', () => {
  const node = mount(<Icon type="plus" size="10px" />);

  expect(node.find('svg')).toHaveProp('style', {
    minWidth: '10px',
    minHeight: '10px',
    maxWidth: '10px',
    maxHeight: '10px',
  });
});
