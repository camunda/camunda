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
    // given
    const node = shallow(<CollapsablePanelProvider />);

    // then
    // Filters panel is not collapsed
    expect(node.prop('value').isFiltersCollapsed).toBe(false);

    // Operations panel is collapsed
    expect(node.prop('value').isOperationsCollapsed).toBe(true);
  });

  describe('toggle', () => {
    it('should toggle target value', () => {
      // (1): isFiltersCollapsed: false
      // given
      const node = shallow(<CollapsablePanelProvider />);
      // when

      // @ts-expect-error ts-migrate(2339) FIXME: Property 'toggle' does not exist on type 'Componen... Remove this comment to see the full error message
      node.instance().toggle('isFiltersCollapsed');
      // then
      expect(node.prop('value').isFiltersCollapsed).toBe(true);

      // (2): isFiltersCollapsed: true
      // when
      // @ts-expect-error ts-migrate(2339) FIXME: Property 'toggle' does not exist on type 'Componen... Remove this comment to see the full error message
      node.instance().toggle('isFiltersCollapsed');
      // then
      expect(node.prop('value').isFiltersCollapsed).toBe(false);
    });

    describe('toggleFilters', () => {
      it('should call toggle with "isFiltersCollapsed"', () => {
        // (1): isFiltersCollapsed: false
        // given
        const node = shallow(<CollapsablePanelProvider />);
        // @ts-expect-error ts-migrate(2769) FIXME: Argument of type '"toggle"' is not assignable to p... Remove this comment to see the full error message
        const toggleSpy = jest.spyOn(node.instance(), 'toggle');

        // when
        // @ts-expect-error ts-migrate(2339) FIXME: Property 'toggleFilters' does not exist on type 'C... Remove this comment to see the full error message
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

        // @ts-expect-error ts-migrate(2769) FIXME: Argument of type '"toggle"' is not assignable to p... Remove this comment to see the full error message
        const toggleSpy = jest.spyOn(node.instance(), 'toggle');

        // when
        // @ts-expect-error ts-migrate(2339) FIXME: Property 'toggleFilters' does not exist on type 'C... Remove this comment to see the full error message
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
      // @ts-expect-error ts-migrate(2339) FIXME: Property 'expand' does not exist on type 'Componen... Remove this comment to see the full error message
      node.instance().expand('isOperationsCollapsed');
      // then
      expect(node.prop('value').isOperationsCollapsed).toBe(false);
    });

    describe('expandFilters', () => {
      it('should call expand with "isFiltersCollapsed', () => {
        // given
        const node = shallow(<CollapsablePanelProvider />);
        // @ts-expect-error ts-migrate(2769) FIXME: Argument of type '"expand"' is not assignable to p... Remove this comment to see the full error message
        const expandSpy = jest.spyOn(node.instance(), 'expand');

        // when
        // @ts-expect-error ts-migrate(2339) FIXME: Property 'expandFilters' does not exist on type 'C... Remove this comment to see the full error message
        node.instance().expandFilters();

        // then
        expect(expandSpy).toBeCalledWith('isFiltersCollapsed');
      });
    });

    describe('expandOperations', () => {
      it('should call expand with "isOperationsCollapsed', () => {
        // given
        const node = shallow(<CollapsablePanelProvider />);
        // @ts-expect-error ts-migrate(2769) FIXME: Argument of type '"expand"' is not assignable to p... Remove this comment to see the full error message
        const expandSpy = jest.spyOn(node.instance(), 'expand');

        // when
        // @ts-expect-error ts-migrate(2339) FIXME: Property 'expandOperations' does not exist on type... Remove this comment to see the full error message
        node.instance().expandOperations();

        // then
        expect(expandSpy).toBeCalledWith('isOperationsCollapsed');
      });
    });
  });
});
