import React from 'react';
import {shallow} from 'enzyme';

import {getWorkflowName} from 'modules/utils/instance';
import {HEADER} from 'modules/constants';

import InstanceLog from './InstanceLog';
import * as Styled from './styled';

const mockProps = {
  instanceLog: {
    workflowId: 'foo',
    activities: [
      {
        state: 'someState1',
        type: 'someType1',
        name: 'someName1',
        id: '1'
      },
      {
        state: 'someState2',
        type: 'someType2',
        name: 'someName2',
        id: '2'
      }
    ]
  }
};

describe('InstanceLog', () => {
  it('should not render empty InstanceLog if there is no instanceLog', () => {
    // given
    const node = shallow(<InstanceLog />);

    // then
    const InstanceLogNode = node.find(Styled.InstanceLog);
    expect(InstanceLogNode).toHaveLength(1);
    expect(InstanceLogNode.children().length).toBe(0);
  });

  it('should render selected header and entries if there is instanceLog', () => {
    // given
    const node = shallow(<InstanceLog {...mockProps} />);
    const {instanceLog} = mockProps;

    // then
    // Header
    const HeaderNode = node.find(Styled.Header);
    expect(HeaderNode).toHaveLength(1);
    expect(HeaderNode.prop('isSelected')).toBe(true);
    expect(HeaderNode.contains(getWorkflowName(instanceLog))).toBe(true);
    // DocumentIcon
    const DocumentIconNode = HeaderNode.find(Styled.DocumentIcon);
    expect(DocumentIconNode).toHaveLength(1);
    expect(DocumentIconNode.prop('isSelected')).toBe(true);
    // Log Entries
    const LogEntryNodes = node.find(Styled.LogEntry);
    expect(LogEntryNodes).toHaveLength(instanceLog.activities.length);
    LogEntryNodes.forEach((LogEntryNode, idx) => {
      expect(LogEntryNode.prop('isSelected')).toBe(false);
      expect(LogEntryNode.contains(instanceLog.activities[idx].name));
      // FlowNodeIcon
      const FlowNodeIconNode = LogEntryNode.find(Styled.FlowNodeIcon);
      expect(FlowNodeIconNode).toHaveLength(1);
      expect(FlowNodeIconNode.prop('isSelected')).toBe(false);
      expect(FlowNodeIconNode.prop('state')).toBe(
        instanceLog.activities[idx].state
      );
      expect(FlowNodeIconNode.prop('type')).toBe(
        instanceLog.activities[idx].type
      );
    });

    // snapshot
    expect(node).toMatchSnapshot();
  });

  it('should change selection when clicking on a log entry', () => {
    // given
    const node = shallow(<InstanceLog {...mockProps} />);
    const {
      instanceLog: {activities}
    } = mockProps;
    const LogEntryNodes = node.find(Styled.LogEntry);

    LogEntryNodes.forEach((LogEntryNode, idx) => {
      // when
      LogEntryNode.simulate('click');
      node.update();
      LogEntryNode = node.find(Styled.LogEntry).at(idx);

      // then
      expect(node.state('selected')).toBe(activities[idx].id);
      expect(LogEntryNode.prop('isSelected')).toBe(true);
      // FlowNodeIcon
      const FlowNodeIconNode = LogEntryNode.find(Styled.FlowNodeIcon);
      expect(FlowNodeIconNode).toHaveLength(1);
      expect(FlowNodeIconNode.prop('isSelected')).toBe(true);
    });
  });

  it('should change selection to header when clicking on it', () => {
    // given
    const node = shallow(<InstanceLog {...mockProps} />);
    const {
      instanceLog: {activities}
    } = mockProps;
    node.setState({selected: activities[0].id});
    node.update();
    let HeaderNode = node.find(Styled.Header);

    // when
    HeaderNode.simulate('click');
    node.update();
    HeaderNode = node.find(Styled.Header);

    // then
    expect(node.state('selected')).toBe(HEADER);
    expect(HeaderNode.prop('isSelected')).toBe(true);
    expect(HeaderNode.find(Styled.DocumentIcon).prop('isSelected')).toBe(true);
  });
});
