/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {shallow} from 'enzyme';

import CollapsibleSection from './CollapsibleSection';

const props = {
  children: null,
  sectionTitle: 'this is a title',
  isSectionOpen: true,
  toggleSectionOpen: jest.fn(),
};

it('should display title', () => {
  const node = shallow(<CollapsibleSection {...props} />);

  expect(node.find('.sectionTitle')).toIncludeText('this is a title');
});

it('should have collapsed class when is not open', () => {
  const node = shallow(<CollapsibleSection {...props} />);

  expect(node.hasClass('collapsed')).toBe(false);

  node.setProps({isSectionOpen: false});

  expect(node.hasClass('collapsed')).toBe(true);
});

it('should call toggle function when section title is clicked', () => {
  const spy = jest.fn();
  const node = shallow(<CollapsibleSection {...props} toggleSectionOpen={spy} />);

  node.find('.sectionTitle').simulate('click');

  expect(spy).toHaveBeenCalled();
});
