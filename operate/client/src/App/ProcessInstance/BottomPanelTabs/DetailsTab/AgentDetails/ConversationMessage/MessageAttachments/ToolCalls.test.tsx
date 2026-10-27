/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {render, screen} from 'modules/testing-library';
import {ToolCalls} from './ToolCalls';

describe('<ToolCalls />', () => {
  it('should render nothing for empty tool calls', () => {
    const {container} = render(<ToolCalls toolCalls={[]} />);

    expect(container).toBeEmptyDOMElement();
  });

  it('should render a chip for each tool call', () => {
    render(
      <ToolCalls
        toolCalls={[
          {
            toolCallId: 'tc-1',
            toolName: 'search_web',
            elementId: null,
            arguments: null,
          },
          {
            toolCallId: 'tc-2',
            toolName: 'run_query',
            elementId: null,
            arguments: null,
          },
        ]}
      />,
    );

    expect(
      screen.getByRole('listitem', {name: 'search_web'}),
    ).toBeInTheDocument();
    expect(
      screen.getByRole('listitem', {name: 'run_query'}),
    ).toBeInTheDocument();
  });
});
