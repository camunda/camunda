import React from 'react';
import {shallow} from 'enzyme';

import {ACTIVITY_STATE, ACTIVITY_TYPE} from 'modules/constants';

import FlowNodeIcon from './FlowNodeIcon';

describe('FlowNodeIcon', () => {
  it('should render icon for COMPLETED TASK', () => {
    expect(
      shallow(
        <FlowNodeIcon
          type={ACTIVITY_TYPE.TASK}
          state={ACTIVITY_STATE.COMPLETED}
        />
      )
    ).toMatchSnapshot();
  });

  it('should render icon for ACTIVE TASK', () => {
    expect(
      shallow(
        <FlowNodeIcon type={ACTIVITY_TYPE.TASK} state={ACTIVITY_STATE.ACTIVE} />
      )
    ).toMatchSnapshot();
  });

  it('should render icon for INCIDENT TASK', () => {
    expect(
      shallow(
        <FlowNodeIcon
          type={ACTIVITY_TYPE.TASK}
          state={ACTIVITY_STATE.INCIDENT}
        />
      )
    ).toMatchSnapshot();
  });

  it('should render icon for TERMINATED TASK', () => {
    expect(
      shallow(
        <FlowNodeIcon
          type={ACTIVITY_TYPE.TASK}
          state={ACTIVITY_STATE.TERMINATED}
        />
      )
    ).toMatchSnapshot();
  });

  it('should render icon for COMPLETED EVENT', () => {
    expect(
      shallow(
        <FlowNodeIcon
          type={ACTIVITY_TYPE.EVENT}
          state={ACTIVITY_STATE.COMPLETED}
        />
      )
    ).toMatchSnapshot();
  });

  it('should render icon for ACTIVE EVENT', () => {
    expect(
      shallow(
        <FlowNodeIcon
          type={ACTIVITY_TYPE.EVENT}
          state={ACTIVITY_STATE.ACTIVE}
        />
      )
    ).toMatchSnapshot();
  });

  it('should render icon for INCIDENT EVENT', () => {
    expect(
      shallow(
        <FlowNodeIcon
          type={ACTIVITY_TYPE.EVENT}
          state={ACTIVITY_STATE.INCIDENT}
        />
      )
    ).toMatchSnapshot();
  });

  it('should render icon for TERMINATED EVENT', () => {
    expect(
      shallow(
        <FlowNodeIcon
          type={ACTIVITY_TYPE.EVENT}
          state={ACTIVITY_STATE.TERMINATED}
        />
      )
    ).toMatchSnapshot();
  });

  it('should render icon for COMPLETED GATEWAY', () => {
    expect(
      shallow(
        <FlowNodeIcon
          type={ACTIVITY_TYPE.GATEWAY}
          state={ACTIVITY_STATE.COMPLETED}
        />
      )
    ).toMatchSnapshot();
  });

  it('should render icon for ACTIVE GATEWAY', () => {
    expect(
      shallow(
        <FlowNodeIcon
          type={ACTIVITY_TYPE.GATEWAY}
          state={ACTIVITY_STATE.ACTIVE}
        />
      )
    ).toMatchSnapshot();
  });

  it('should render icon for INCIDENT GATEWAY', () => {
    expect(
      shallow(
        <FlowNodeIcon
          type={ACTIVITY_TYPE.GATEWAY}
          state={ACTIVITY_STATE.INCIDENT}
        />
      )
    ).toMatchSnapshot();
  });

  it('should render icon for TERMINATED GATEWAY', () => {
    expect(
      shallow(
        <FlowNodeIcon
          type={ACTIVITY_TYPE.GATEWAY}
          state={ACTIVITY_STATE.TERMINATED}
        />
      )
    ).toMatchSnapshot();
  });
});
