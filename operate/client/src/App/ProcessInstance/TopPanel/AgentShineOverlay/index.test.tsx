/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {AgentShineOverlay} from './index';
import {render, screen} from 'modules/testing-library';

describe('AgentShineOverlay', () => {
  it('should render the shine box at the element size plus an offset border', () => {
    render(
      <AgentShineOverlay
        container={document.body}
        elementId="Activity_agent"
      />,
      {
        wrapper: ({children}) => (
          <div>
            <svg>
              <g data-element-id="Activity_agent">
                <g className="djs-visual">
                  <rect width="120" height="80" rx="10"></rect>
                </g>
              </g>
            </svg>
            {children}
          </div>
        ),
      },
    );

    const shine = screen.getByTestId('agent-shine-overlay-Activity_agent');
    expect(shine).toHaveStyle({
      width: '122px',
      height: '82px',
      'border-radius': '11px',
    });
  });

  it('should render nothing when the BPMN visual node is missing', () => {
    render(
      <AgentShineOverlay
        container={document.body}
        elementId="Activity_missing"
      />,
    );

    expect(
      screen.queryByTestId('agent-shine-overlay-Activity_missing'),
    ).not.toBeInTheDocument();
  });
});
