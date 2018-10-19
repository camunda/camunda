import React from 'react';
import {shallow} from 'enzyme';
import {CollapsablePanelProvider} from './CollapsablePanelContext';

describe('CollapsablePanelProvider', () => {
  it('should store default panels collabsable states', () => {
    const node = new CollapsablePanelProvider();

    // Filters panel is not collapsed
    expect(node.state.isFiltersCollapsed).toBe(false);

    // Selections panel is not collapsed
    expect(node.state.isSelectionsCollapsed).toBe(true);
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
      node.instance().expand('isSelectionsCollapsed');

      // then
      expect(node.state('isSelectionsCollapsed')).toBe(false);
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

    describe('expandSelections', () => {
      it('should call expand with "isSelectionsCollapsed', () => {
        // given
        const node = shallow(<CollapsablePanelProvider />);
        const expandSpy = jest.spyOn(node.instance(), 'expand');

        // when
        node.instance().expandSelections();

        // then
        expect(expandSpy).toBeCalledWith('isSelectionsCollapsed');
      });
    });
  });
});
