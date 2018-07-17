import React from 'react';
import {shallow} from 'enzyme';

import {DEFAULT_FILTER, FILTER_TYPES, DIRECTION} from 'modules/constants';
import Button from 'modules/components/Button';

import Filters from './Filters';
import Filter from './Filter';
import * as Styled from './styled';

describe('Filters', () => {
  const spy = jest.fn();
  const mockProps = {
    filter: {active: true, incidents: false, canceled: true, completed: false},
    onFilterChange: spy,
    resetFilter: jest.fn(),
    onExtraFilterChange: jest.fn()
  };

  beforeEach(() => {
    spy.mockClear();
  });

  it('should render the filters', () => {
    // given
    const {
      filter: {active, incidents, completed, canceled}
    } = mockProps;
    const node = shallow(<Filters {...mockProps} />);
    const FilterNodes = node.find(Filter);

    // then
    expect(FilterNodes).toHaveLength(2);
    expect(FilterNodes.at(0).prop('type')).toBe(FILTER_TYPES.RUNNING);
    expect(FilterNodes.at(0).prop('filter')).toEqual({active, incidents});
    expect(FilterNodes.at(0).prop('onChange')).toBe(mockProps.onFilterChange);
    expect(FilterNodes.at(1).prop('type')).toBe(FILTER_TYPES.FINISHED);
    expect(FilterNodes.at(1).prop('filter')).toEqual({completed, canceled});
    expect(FilterNodes.at(1).prop('onChange')).toBe(mockProps.onFilterChange);
  });

  it('should render the expand button with left direction', () => {
    // given
    const node = shallow(<Filters {...mockProps} />);
    const ExpandButtonNode = node.find(Styled.ExpandButton);

    // then
    expect(ExpandButtonNode).toHaveLength(1);
    expect(ExpandButtonNode.prop('direction')).toBe(DIRECTION.LEFT);
    expect(ExpandButtonNode.prop('isExpanded')).toBe(true);
  });

  it('should render the non disabled reset filters button', () => {
    // given
    const node = shallow(<Filters {...mockProps} />);
    const ResetButtonNode = node.find(Button);

    // then
    expect(ResetButtonNode).toHaveLength(1);
    expect(ResetButtonNode.prop('disabled')).toBe(false);
    expect(ResetButtonNode.prop('onClick')).toBe(mockProps.resetFilter);
  });

  it('should render the non disabled reset filters button', () => {
    // given
    const node = shallow(<Filters {...mockProps} filter={DEFAULT_FILTER} />);
    const ResetButtonNode = node.find(Button);

    // then
    expect(ResetButtonNode).toHaveLength(1);
    expect(ResetButtonNode.prop('disabled')).toBe(true);
  });

  describe('errorMessage filter', () => {
    it('should render an errorMessage field', () => {
      // given
      const node = shallow(<Filters {...mockProps} filter={DEFAULT_FILTER} />);
      const errorMessageNode = node.find({name: 'errorMessage'});

      // then
      expect(errorMessageNode.length).toEqual(1);
      expect(errorMessageNode.props().onBlur).toEqual(
        node.instance().handleErrorMessageChange
      );
    });

    it('should call onFilterChange with the right error message', () => {
      const errorMessage = 'lorem ipsum';
      // given
      const node = shallow(<Filters {...mockProps} filter={DEFAULT_FILTER} />);

      //when
      node.instance().handleErrorMessageChange({target: {value: errorMessage}});

      // then
      expect(spy).toHaveBeenCalledWith({errorMessage});
    });

    it('should call onFilterChange with empty error message', () => {
      // given
      // user blurs without writing
      const emptyErrorMessage = '';
      const node = shallow(<Filters {...mockProps} filter={DEFAULT_FILTER} />);

      //when
      node
        .instance()
        .handleErrorMessageChange({target: {value: emptyErrorMessage}});

      // then
      expect(spy).toHaveBeenCalledWith({errorMessage: null});
    });
  });

  describe('instanceIds filter', () => {
    it('should render an instanceIds field', () => {
      // given
      const node = shallow(<Filters {...mockProps} filter={DEFAULT_FILTER} />);
      const instanceIdsNode = node.find({name: 'instanceIds'});

      // then
      expect(instanceIdsNode.length).toEqual(1);
      expect(instanceIdsNode.props().onBlur).toEqual(
        node.instance().handleInstanceIdsChange
      );
    });

    it('should call onFilterChange with the right instance ids', () => {
      const instanceIds = '4294968008,4294972032  4294974064, 4294976280, ,';
      // given
      const node = shallow(<Filters {...mockProps} filter={DEFAULT_FILTER} />);

      //when
      node.instance().handleInstanceIdsChange({target: {value: instanceIds}});

      // then
      expect(spy).toHaveBeenCalledWith({
        ids: ['4294968008', '4294972032', '4294974064', '4294976280']
      });
    });

    it('should call onFilterChange with an empty array', () => {
      // given
      // user blurs without writing
      const emptyInstanceIds = '';
      const node = shallow(<Filters {...mockProps} filter={DEFAULT_FILTER} />);

      //when
      node
        .instance()
        .handleInstanceIdsChange({target: {value: emptyInstanceIds}});

      // then
      expect(spy).toHaveBeenCalledWith({
        ids: []
      });
    });
  });
});
