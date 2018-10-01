import React from 'react';
import {shallow} from 'enzyme';

import {ACTIVITY_STATE, FLOW_NODE_TYPE} from 'modules/constants';

import ThemedFlowNodeIcon from './FlowNodeIcon';
import * as Styled from './styled';

const FlowNodeIcon = ThemedFlowNodeIcon.WrappedComponent;

describe('FlowNodeIcon', () => {
  describe('flow node type', () => {
    it('should render task icon', () => {
      // given
      const node = shallow(
        <FlowNodeIcon
          type={FLOW_NODE_TYPE.TASK}
          state={ACTIVITY_STATE.ACTIVE}
          theme="dark"
        />
      );

      // then
      expect(node.find(Styled.TaskDefault)).toHaveLength(1);
      expect(node).toMatchSnapshot();
    });

    it('should render start event icon', () => {
      // given
      const node = shallow(
        <FlowNodeIcon
          type={FLOW_NODE_TYPE.START_EVENT}
          state={ACTIVITY_STATE.ACTIVE}
          theme="dark"
        />
      );

      // then
      expect(node.find(Styled.StartEvent)).toHaveLength(1);
      expect(node).toMatchSnapshot();
    });

    it('should render end event icon', () => {
      // given
      const node = shallow(
        <FlowNodeIcon
          type={FLOW_NODE_TYPE.END_EVENT}
          state={ACTIVITY_STATE.ACTIVE}
          theme="dark"
        />
      );

      // then
      expect(node.find(Styled.EndEvent)).toHaveLength(1);
      expect(node).toMatchSnapshot();
    });

    it('should render exclusive gateway icon', () => {
      // given
      const node = shallow(
        <FlowNodeIcon
          type={FLOW_NODE_TYPE.EXCLUSIVE_GATEWAY}
          state={ACTIVITY_STATE.ACTIVE}
          theme="dark"
        />
      );

      // then
      expect(node.find(Styled.ExclusiveGateway)).toHaveLength(1);
      expect(node).toMatchSnapshot();
    });

    it('should render exclusive parallel icon', () => {
      // given
      const node = shallow(
        <FlowNodeIcon
          type={FLOW_NODE_TYPE.PARALLEL_GATEWAY}
          state={ACTIVITY_STATE.ACTIVE}
          theme="dark"
        />
      );

      // then
      expect(node.find(Styled.ParallelGateway)).toHaveLength(1);
      expect(node).toMatchSnapshot();
    });
  });

  describe('flow node state', () => {
    it('should render completed dark icon', () => {
      // given
      const node = shallow(
        <FlowNodeIcon
          type={FLOW_NODE_TYPE.TASK}
          state={ACTIVITY_STATE.COMPLETED}
          theme="dark"
        />
      );

      // then
      expect(node.find(Styled.CompletedDark)).toHaveLength(1);
      expect(node).toMatchSnapshot();
    });

    it('should render completed light icon', () => {
      // given
      const node = shallow(
        <FlowNodeIcon
          type={FLOW_NODE_TYPE.TASK}
          state={ACTIVITY_STATE.COMPLETED}
          theme="light"
        />
      );

      // then
      expect(node.find(Styled.CompletedLight)).toHaveLength(1);
      expect(node).toMatchSnapshot();
    });

    it('should render completed selected icon', () => {
      // given
      const node = shallow(
        <FlowNodeIcon
          type={FLOW_NODE_TYPE.TASK}
          state={ACTIVITY_STATE.COMPLETED}
          theme="dark"
          isSelected={true}
        />
      );

      // then
      expect(node.find(Styled.CompletedSelected)).toHaveLength(1);
      expect(node).toMatchSnapshot();
    });

    it('should render canceled dark icon', () => {
      // given
      const node = shallow(
        <FlowNodeIcon
          type={FLOW_NODE_TYPE.TASK}
          state={ACTIVITY_STATE.TERMINATED}
          theme="dark"
        />
      );

      // then
      expect(node.find(Styled.CanceledDark)).toHaveLength(1);
      expect(node).toMatchSnapshot();
    });

    it('should render canceled light icon', () => {
      // given
      const node = shallow(
        <FlowNodeIcon
          type={FLOW_NODE_TYPE.TASK}
          state={ACTIVITY_STATE.TERMINATED}
          theme="light"
        />
      );

      // then
      expect(node.find(Styled.CanceledLight)).toHaveLength(1);
      expect(node).toMatchSnapshot();
    });

    it('should render canceled selected icon', () => {
      // given
      const node = shallow(
        <FlowNodeIcon
          type={FLOW_NODE_TYPE.TASK}
          state={ACTIVITY_STATE.TERMINATED}
          theme="dark"
          isSelected={true}
        />
      );

      // then
      expect(node.find(Styled.CanceledSelected)).toHaveLength(1);
      expect(node).toMatchSnapshot();
    });

    it('should render active dark icon', () => {
      // given
      const node = shallow(
        <FlowNodeIcon
          type={FLOW_NODE_TYPE.TASK}
          state={ACTIVITY_STATE.ACTIVE}
          theme="dark"
        />
      );

      // then
      expect(node.find(Styled.OkDark)).toHaveLength(1);
      expect(node).toMatchSnapshot();
    });

    it('should render active light icon', () => {
      // given
      const node = shallow(
        <FlowNodeIcon
          type={FLOW_NODE_TYPE.TASK}
          state={ACTIVITY_STATE.ACTIVE}
          theme="light"
        />
      );

      // then
      expect(node.find(Styled.OkLight)).toHaveLength(1);
      expect(node).toMatchSnapshot();
    });

    it("should render active light icon if it's selected", () => {
      // given
      const node = shallow(
        <FlowNodeIcon
          type={FLOW_NODE_TYPE.TASK}
          state={ACTIVITY_STATE.ACTIVE}
          theme="dark"
          isSelected={true}
        />
      );

      // then
      expect(node.find(Styled.OkLight)).toHaveLength(1);
      expect(node).toMatchSnapshot();
    });

    it('should render incident dark icon', () => {
      // given
      const node = shallow(
        <FlowNodeIcon
          type={FLOW_NODE_TYPE.TASK}
          state={ACTIVITY_STATE.INCIDENT}
          theme="dark"
        />
      );

      // then
      expect(node.find(Styled.IncidentDark)).toHaveLength(1);
      expect(node).toMatchSnapshot();
    });

    it('should render incident light icon', () => {
      // given
      const node = shallow(
        <FlowNodeIcon
          type={FLOW_NODE_TYPE.TASK}
          state={ACTIVITY_STATE.INCIDENT}
          theme="light"
        />
      );

      // then
      expect(node.find(Styled.IncidentLight)).toHaveLength(1);
      expect(node).toMatchSnapshot();
    });

    it("should render incident light icon if it's selected", () => {
      // given
      const node = shallow(
        <FlowNodeIcon
          type={FLOW_NODE_TYPE.TASK}
          state={ACTIVITY_STATE.INCIDENT}
          theme="dark"
          isSelected={true}
        />
      );

      // then
      expect(node.find(Styled.IncidentLight)).toHaveLength(1);
      expect(node).toMatchSnapshot();
    });
  });
});
