/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {shallow} from 'enzyme';
import {CollapsablePanelProvider} from './CollapsablePanelContext';

describe('CollapsablePanelProvider', () => {
  it('should store default panels collabsable states', () => {
    const node = new CollapsablePanelProvider();

    // Filters panel is not collapsed
    expect(node.state.isFiltersCollapsed).toBe(false);

    // Operations panel is not collapsed
    expect(node.state.isOperationsCollapsed).toBe(true);
  });

  describe('toggle', () => {
    it('should toggle target value', () => {
      // (1): isFiltersCollapsed: false
      // given
      const node = shallow(<CollapsablePanelProvider />);
      // when
      node.instance().toggle('isFiltersCollapsed');
      // then
      expect(node.state('isFiltersCollapsed')).toBe(true);

      // (2): isFiltersCollapsed: true
      // when
      node.instance().toggle('isFiltersCollapsed');
      // then
      expect(node.state('isFiltersCollapsed')).toBe(false);
    });

    describe('toggleFilters', () => {
      it('should call toggle with "isFiltersCollapsed"', () => {
        // (1): isFiltersCollapsed: false
        // given
        const node = shallow(<CollapsablePanelProvider />);
        const toggleSpy = jest.spyOn(node.instance(), 'toggle');

        // when
        node.instance().toggleFilters();

        // then
        expect(toggleSpy).toBeCalledWith('isFiltersCollapsed');
      });
    });
    describe('toggleFilters', () => {
      it('should call toggle with "isFiltersCollapsed"', () => {
        // (1): isFiltersCollapsed: false
        // given
        const node = shallow(<CollapsablePanelProvider />);
        const toggleSpy = jest.spyOn(node.instance(), 'toggle');

        // when
        node.instance().toggleFilters();

        // then
        expect(toggleSpy).toBeCalledWith('isFiltersCollapsed');
      });
    });
  });

  describe('expand', () => {
    it('should expand target', () => {
      // given
      const node = shallow(<CollapsablePanelProvider />);

      // when
      node.instance().expand('isOperationsCollapsed');

      // then
      expect(node.state('isOperationsCollapsed')).toBe(false);
    });

    describe('expandFilters', () => {
      it('should call expand with "isFiltersCollapsed', () => {
        // given
        const node = shallow(<CollapsablePanelProvider />);
        const expandSpy = jest.spyOn(node.instance(), 'expand');

        // when
        node.instance().expandFilters();

        // then
        expect(expandSpy).toBeCalledWith('isFiltersCollapsed');
      });
    });

    describe('expandOperations', () => {
      it('should call expand with "isOperationsCollapsed', () => {
        // given
        const node = shallow(<CollapsablePanelProvider />);
        const expandSpy = jest.spyOn(node.instance(), 'expand');

        // when
        node.instance().expandOperations();

        // then
        expect(expandSpy).toBeCalledWith('isOperationsCollapsed');
      });
    });
  });
});
