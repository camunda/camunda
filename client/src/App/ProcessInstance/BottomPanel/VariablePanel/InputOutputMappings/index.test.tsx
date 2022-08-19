/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {ThemeProvider} from 'modules/theme/ThemeProvider';
import {InputOutputMappings} from './index';
import {render, screen, waitFor} from 'modules/testing-library';
import {rest} from 'msw';
import {mockServer} from 'modules/mock-server/node';
import {mockProcessWithInputOutputMappingsXML} from 'modules/testUtils';
import {processInstanceDetailsDiagramStore} from 'modules/stores/processInstanceDetailsDiagram';
import {flowNodeSelectionStore} from 'modules/stores/flowNodeSelection';

describe('Input Mappings', () => {
  afterEach(() => {
    flowNodeSelectionStore.reset();
    processInstanceDetailsDiagramStore.reset();
  });

  it('should display empty message', async () => {
    flowNodeSelectionStore.setSelection({
      flowNodeId: 'Activity_0qtp1k6',
    });
    const {rerender} = render(<InputOutputMappings type="Input" />, {
      wrapper: ThemeProvider,
    });

    expect(screen.getByText('No Input Mappings defined')).toBeInTheDocument();

    rerender(<InputOutputMappings type="Output" />);
    expect(screen.getByText('No Output Mappings defined')).toBeInTheDocument();
  });

  it('should display input mappings', async () => {
    mockServer.use(
      rest.get(`/api/processes/:processId/xml`, (_, res, ctx) =>
        res.once(ctx.text(mockProcessWithInputOutputMappingsXML))
      )
    );

    await processInstanceDetailsDiagramStore.fetchProcessXml('processId');
    flowNodeSelectionStore.setSelection({
      flowNodeId: 'Activity_0qtp1k6',
    });

    render(<InputOutputMappings type="Input" />, {wrapper: ThemeProvider});
    await waitFor(() =>
      expect(processInstanceDetailsDiagramStore.state.status).toBe('fetched')
    );

    expect(screen.getByText(/local variable name/i)).toBeInTheDocument();
    expect(screen.getByText(/variable assignment value/i)).toBeInTheDocument();
    expect(screen.getByText(/localVariable1/i)).toBeInTheDocument();
    expect(screen.getByText(/= "test1"/i)).toBeInTheDocument();
    expect(screen.getByText(/localVariable2/i)).toBeInTheDocument();
    expect(screen.getByText(/= "test2"/i)).toBeInTheDocument();
  });

  it('should display output mappings', async () => {
    mockServer.use(
      rest.get(`/api/processes/:processId/xml`, (_, res, ctx) =>
        res.once(ctx.text(mockProcessWithInputOutputMappingsXML))
      )
    );

    await processInstanceDetailsDiagramStore.fetchProcessXml('processId');
    flowNodeSelectionStore.setSelection({
      flowNodeId: 'Activity_0qtp1k6',
    });

    render(<InputOutputMappings type="Output" />, {wrapper: ThemeProvider});
    await waitFor(() =>
      expect(processInstanceDetailsDiagramStore.state.status).toBe('fetched')
    );

    expect(screen.getByText(/process variable name/i)).toBeInTheDocument();
    expect(screen.getByText(/variable assignment value/i)).toBeInTheDocument();
    expect(screen.getByText(/outputTest/i)).toBeInTheDocument();
    expect(screen.getByText(/= 2/i)).toBeInTheDocument();
  });

  it.each([
    {
      type: 'Input',
      message:
        'Input mappings are defined while modelling the diagram. They are used to create new local variables inside the flow node scope with the specified assignment.',
    },
    {
      type: 'Output',
      message:
        'Output mappings are defined while modelling the diagram. They are used to control the variable propagation from the flow node scope. Process variables in the parent scopes are created/updated with the specified assignment.',
    },
  ])(
    'should display/hide information banner for input/output mappings',
    async ({type, message}) => {
      mockServer.use(
        rest.get(`/api/processes/:processId/xml`, (_, res, ctx) =>
          res.once(ctx.text(mockProcessWithInputOutputMappingsXML))
        )
      );

      await processInstanceDetailsDiagramStore.fetchProcessXml('processId');
      flowNodeSelectionStore.setSelection({
        flowNodeId: 'some-flow-node',
      });

      const {user, rerender} = render(
        <InputOutputMappings type={type as 'Input' | 'Output'} />,
        {
          wrapper: ThemeProvider,
        }
      );
      expect(screen.getByText(message)).toBeInTheDocument();

      await user.click(screen.getByRole('button', {name: 'Close'}));

      expect(screen.queryByText(message)).not.toBeInTheDocument();

      rerender(<InputOutputMappings type={type as 'Input' | 'Output'} />);
      expect(screen.queryByText(message)).not.toBeInTheDocument();
    }
  );
});
