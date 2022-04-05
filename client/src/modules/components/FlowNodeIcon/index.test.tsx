/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React from 'react';
import {render, screen} from '@testing-library/react';
import FlowNodeIcon from './index';
import {ThemeProvider} from 'modules/theme/ThemeProvider';
import {TYPE, MULTI_INSTANCE_TYPE} from 'modules/constants';

describe('FlowNodeIcon', () => {
  it('should render default icon', () => {
    render(<FlowNodeIcon types={{}} flowNodeInstanceType={''} />, {
      wrapper: ThemeProvider,
    });

    expect(
      screen.getByText('flow-node-task-undefined.svg')
    ).toBeInTheDocument();
  });

  it('should render parallel multi instance body', () => {
    render(
      <FlowNodeIcon
        types={{
          elementType: TYPE.TASK_SUBPROCESS,
          multiInstanceType: MULTI_INSTANCE_TYPE.PARALLEL,
        }}
        flowNodeInstanceType={TYPE.MULTI_INSTANCE_BODY}
      />,
      {
        wrapper: ThemeProvider,
      }
    );

    expect(
      screen.getByText('flow-node-multi-instance-sequential.svg')
    ).toBeInTheDocument();
  });

  it('should render sequential multi instance body', () => {
    render(
      <FlowNodeIcon
        types={{
          elementType: TYPE.TASK_SUBPROCESS,
          multiInstanceType: MULTI_INSTANCE_TYPE.SEQUENTIAL,
        }}
        flowNodeInstanceType={TYPE.MULTI_INSTANCE_BODY}
      />,
      {
        wrapper: ThemeProvider,
      }
    );

    expect(
      screen.getByText('flow-node-multi-instance-parallel.svg')
    ).toBeInTheDocument();
  });

  it('should render intermediate timer event', () => {
    render(
      <FlowNodeIcon
        types={{
          elementType: TYPE.EVENT_INTERMEDIATE_CATCH,
          eventType: TYPE.EVENT_TIMER,
        }}
        flowNodeInstanceType={TYPE.EVENT_INTERMEDIATE_CATCH}
      />,
      {
        wrapper: ThemeProvider,
      }
    );

    expect(
      screen.getByText('flow-node-event-timer-interrupting.svg')
    ).toBeInTheDocument();
  });

  it('should render message boundary event', () => {
    render(
      <FlowNodeIcon
        types={{
          elementType: TYPE.EVENT_BOUNDARY_NON_INTERRUPTING,
          eventType: TYPE.EVENT_MESSAGE,
        }}
        flowNodeInstanceType={TYPE.EVENT_BOUNDARY_NON_INTERRUPTING}
      />,
      {
        wrapper: ThemeProvider,
      }
    );

    expect(
      screen.getByText('flow-node-event-message-non-interrupting.svg')
    ).toBeInTheDocument();
  });
});
