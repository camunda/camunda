import React from 'react';
import {shallow} from 'enzyme';

import {getWorkflowName} from 'modules/utils/instance';
import {HEADER} from 'modules/constants';

import InstanceLog from './InstanceLog';
import * as Styled from './styled';

const mockProps = {
  selectedLogEntry: HEADER,
  onSelect: jest.fn(),
  instance: {
    workflowId: 'foo'
  },
  activitiesDetails: {
    1: {
      state: 'someState1',
      type: 'someType1',
      name: 'someName1'
    },
    2: {
      state: 'someState2',
      type: 'someType2',
      name: 'someName2'
    }
  }
};

describe('InstanceLog', () => {
  beforeEach(() => {
    mockProps.onSelect.mockClear();
  });

  it('should only render selected header if there is instance and no activitiesDetails', () => {
    // given
    const {activitiesDetails, ...props} = mockProps;
    const node = shallow(<InstanceLog {...props} />);

    // then
    const HeaderNode = node.find(Styled.HeaderToggle);
    expect(HeaderNode).toHaveLength(1);
    expect(HeaderNode.prop('isSelected')).toBe(true);
    expect(HeaderNode.contains(getWorkflowName(mockProps.instance))).toBe(true);
    // DocumentIcon
    const DocumentIconNode = HeaderNode.find(Styled.DocumentIcon);
    expect(DocumentIconNode).toHaveLength(1);
    expect(DocumentIconNode.prop('isSelected')).toBe(true);

    // Log Entries
    expect(node.find(Styled.LogEntryToggle)).toHaveLength(0);
  });

  it('should render entries if there are actvitiesDetails', () => {
    // given
    const node = shallow(<InstanceLog {...mockProps} />);
    const activitiesDetailsEntries = Object.entries(
      mockProps.activitiesDetails
    );

    // then
    // Log Entries
    const LogEntryToggleNodes = node.find(Styled.LogEntryToggle);
    expect(LogEntryToggleNodes).toHaveLength(
      Object.keys(activitiesDetailsEntries).length
    );
    LogEntryToggleNodes.forEach((LogEntryToggleNode, idx) => {
      expect(LogEntryToggleNode.prop('isSelected')).toBe(false);
      expect(
        LogEntryToggleNode.contains(activitiesDetailsEntries[idx][1].name)
      );
      // FlowNodeIcon
      const FlowNodeIconNode = LogEntryToggleNode.find(Styled.FlowNodeIcon);
      expect(FlowNodeIconNode).toHaveLength(1);
      expect(FlowNodeIconNode.prop('isSelected')).toBe(false);
      expect(FlowNodeIconNode.prop('state')).toBe(
        activitiesDetailsEntries[idx][1].state
      );
      expect(FlowNodeIconNode.prop('type')).toBe(
        activitiesDetailsEntries[idx][1].type
      );
    });

    // snapshot
    expect(node).toMatchSnapshot();
  });

  describe('selection', () => {
    it('should select header ROW on prop.selectedLogEntry', () => {
      // given
      const node = shallow(
        <InstanceLog {...mockProps} selectedLogEntry={HEADER} />
      );
      let HeaderToggleNode = node.find(Styled.HeaderToggle);

      // then
      expect(HeaderToggleNode.prop('isSelected')).toBe(true);
      expect(
        HeaderToggleNode.find(Styled.DocumentIcon).prop('isSelected')
      ).toBe(true);
    });

    it('should select row based on prop.selectedLogEntry', () => {
      // given
      const node = shallow(<InstanceLog {...mockProps} selectedLogEntry="1" />);

      // then
      const LogEntryToggleNode = node.find(Styled.LogEntryToggle).at(0);
      expect(LogEntryToggleNode.prop('isSelected')).toBe(true);
      const FlowNodeIconNode = LogEntryToggleNode.find(Styled.FlowNodeIcon);
      expect(FlowNodeIconNode).toHaveLength(1);
      expect(FlowNodeIconNode.prop('isSelected')).toBe(true);
    });

    it('should change selection when clicking on a log entry', () => {
      // given
      const node = shallow(<InstanceLog {...mockProps} />);
      const activitiesDetailsEntries = Object.entries(
        mockProps.activitiesDetails
      );
      const LogEntryToggleNodes = node.find(Styled.LogEntryToggle);

      LogEntryToggleNodes.forEach((LogEntryToggleNode, idx) => {
        // when
        LogEntryToggleNode.simulate('click');

        // then
        expect(mockProps.onSelect).toBeCalledWith(
          activitiesDetailsEntries[idx][0]
        );
      });
    });

    it('should change selection to header when clicking on it', () => {
      // given
      const node = shallow(
        <InstanceLog {...mockProps} selectedLogEntry="foo" />
      );
      let HeaderToggleNode = node.find(Styled.HeaderToggle);

      // when
      HeaderToggleNode.simulate('click');

      // then
      expect(mockProps.onSelect).toBeCalledWith(HEADER);
    });
  });
});
