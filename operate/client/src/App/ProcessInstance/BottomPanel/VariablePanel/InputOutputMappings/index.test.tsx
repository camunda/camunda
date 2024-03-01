/*
 * Copyright Camunda Services GmbH
 *
 * BY INSTALLING, DOWNLOADING, ACCESSING, USING, OR DISTRIBUTING THE SOFTWARE, YOU INDICATE YOUR ACCEPTANCE TO AND ARE ENTERING INTO A CONTRACT WITH, THE LICENSOR ON THE TERMS SET OUT IN THIS AGREEMENT. IF YOU DO NOT AGREE TO THESE TERMS, YOU MUST NOT USE THE SOFTWARE. IF YOU ARE RECEIVING THE SOFTWARE ON BEHALF OF A LEGAL ENTITY, YOU REPRESENT AND WARRANT THAT YOU HAVE THE ACTUAL AUTHORITY TO AGREE TO THE TERMS AND CONDITIONS OF THIS AGREEMENT ON BEHALF OF SUCH ENTITY.
 * "Licensee" means you, an individual, or the entity on whose behalf you receive the Software.
 *
 * Permission is hereby granted, free of charge, to the Licensee obtaining a copy of this Software and associated documentation files to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject in each case to the following conditions:
 * Condition 1: If the Licensee distributes the Software or any derivative works of the Software, the Licensee must attach this Agreement.
 * Condition 2: Without limiting other conditions in this Agreement, the grant of rights is solely for non-production use as defined below.
 * "Non-production use" means any use of the Software that is not directly related to creating products, services, or systems that generate revenue or other direct or indirect economic benefits.  Examples of permitted non-production use include personal use, educational use, research, and development. Examples of prohibited production use include, without limitation, use for commercial, for-profit, or publicly accessible systems or use for commercial or revenue-generating purposes.
 *
 * If the Licensee is in breach of the Conditions, this Agreement, including the rights granted under it, will automatically terminate with immediate effect.
 *
 * SUBJECT AS SET OUT BELOW, THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE, AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 * NOTHING IN THIS AGREEMENT EXCLUDES OR RESTRICTS A PARTY’S LIABILITY FOR (A) DEATH OR PERSONAL INJURY CAUSED BY THAT PARTY’S NEGLIGENCE, (B) FRAUD, OR (C) ANY OTHER LIABILITY TO THE EXTENT THAT IT CANNOT BE LAWFULLY EXCLUDED OR RESTRICTED.
 */

import {InputOutputMappings} from './index';
import {render, screen, waitFor} from 'modules/testing-library';
import {mockProcessWithInputOutputMappingsXML} from 'modules/testUtils';
import {processInstanceDetailsDiagramStore} from 'modules/stores/processInstanceDetailsDiagram';
import {flowNodeSelectionStore} from 'modules/stores/flowNodeSelection';
import {mockFetchProcessXML} from 'modules/mocks/api/processes/fetchProcessXML';
import {useEffect} from 'react';

const Wrapper = ({children}: {children?: React.ReactNode}) => {
  useEffect(() => {
    return () => {
      flowNodeSelectionStore.reset();
      processInstanceDetailsDiagramStore.reset();
    };
  }, []);

  return <>{children}</>;
};

describe('Input Mappings', () => {
  beforeEach(() =>
    mockFetchProcessXML().withSuccess(mockProcessWithInputOutputMappingsXML),
  );

  it('should display empty message', async () => {
    await processInstanceDetailsDiagramStore.fetchProcessXml('processId');
    flowNodeSelectionStore.setSelection({
      flowNodeId: 'Event_0bonl61',
    });
    const {rerender} = render(<InputOutputMappings type="Input" />, {
      wrapper: Wrapper,
    });

    expect(screen.getByText('No Input Mappings defined')).toBeInTheDocument();

    rerender(<InputOutputMappings type="Output" />);
    expect(screen.getByText('No Output Mappings defined')).toBeInTheDocument();
  });

  it('should display input mappings', async () => {
    await processInstanceDetailsDiagramStore.fetchProcessXml('processId');
    flowNodeSelectionStore.setSelection({
      flowNodeId: 'Activity_0qtp1k6',
    });

    render(<InputOutputMappings type="Input" />, {wrapper: Wrapper});
    await waitFor(() =>
      expect(processInstanceDetailsDiagramStore.state.status).toBe('fetched'),
    );

    expect(screen.getByText(/local variable name/i)).toBeInTheDocument();
    expect(screen.getByText(/variable assignment value/i)).toBeInTheDocument();
    expect(screen.getByText(/localVariable1/i)).toBeInTheDocument();
    expect(screen.getByText(/= "test1"/i)).toBeInTheDocument();
    expect(screen.getByText(/localVariable2/i)).toBeInTheDocument();
    expect(screen.getByText(/= "test2"/i)).toBeInTheDocument();
  });

  it('should display output mappings', async () => {
    await processInstanceDetailsDiagramStore.fetchProcessXml('processId');
    flowNodeSelectionStore.setSelection({
      flowNodeId: 'Activity_0qtp1k6',
    });

    render(<InputOutputMappings type="Output" />, {wrapper: Wrapper});
    await waitFor(() =>
      expect(processInstanceDetailsDiagramStore.state.status).toBe('fetched'),
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
      await processInstanceDetailsDiagramStore.fetchProcessXml('processId');
      flowNodeSelectionStore.setSelection({
        flowNodeId: 'Activity_0qtp1k6',
      });

      const {user, rerender} = render(
        <InputOutputMappings type={type as 'Input' | 'Output'} />,
        {
          wrapper: Wrapper,
        },
      );
      expect(screen.getByText(message)).toBeInTheDocument();

      await user.click(screen.getByRole('button', {name: /close/}));

      expect(screen.queryByText(message)).not.toBeInTheDocument();

      rerender(<InputOutputMappings type={type as 'Input' | 'Output'} />);
      expect(screen.queryByText(message)).not.toBeInTheDocument();
    },
  );
});
