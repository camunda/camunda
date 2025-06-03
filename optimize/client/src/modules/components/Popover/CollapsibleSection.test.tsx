/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {mount} from 'enzyme';
import CollapsibleSection, {CollapsibleSectionContext} from './CollapsibleSection';

const props = {
  children: null,
  sectionTitle: 'this is a title',
  isSectionOpen: true,
  toggleSectionOpen: jest.fn(),
};

const createWrapper = (overrideProps = {}) =>
  mount(
    <CollapsibleSectionContext.Provider value={{calculateDialogStyle: jest.fn()}}>
      <CollapsibleSection {...props} {...overrideProps} />
    </CollapsibleSectionContext.Provider>
  );

it('should display title', () => {
  const node = createWrapper();
  expect(node.find('.sectionTitle')).toIncludeText('this is a title');
});

it('should have collapsed class when section is not open', () => {
  const node = createWrapper({isSectionOpen: false});

  expect(node.find('.CollapsibleSection').hasClass('collapsed')).toBe(true);
});

it('should call toggle function when section title is clicked', () => {
  const spy = jest.fn();
  const node = createWrapper({toggleSectionOpen: spy});
  node.find('.sectionTitle').simulate('click', {preventDefault: () => {}});
  expect(spy).toHaveBeenCalled();
});
