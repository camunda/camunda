/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {render, screen} from 'modules/testing-library';
import {StartProcessFromForm} from './index';
import {MemoryRouter, Route, Routes} from 'react-router-dom';
import {MockThemeProvider} from 'modules/theme/MockProvider';

const getWrapper = ({
  initialEntries,
}: Pick<React.ComponentProps<typeof MemoryRouter>, 'initialEntries'>) => {
  const Wrapper: React.FC<{
    children?: React.ReactNode;
  }> = ({children}) => (
    <MockThemeProvider>
      <MemoryRouter initialEntries={initialEntries}>
        <Routes>
          <Route path="/new/:bpmnProcessId" element={children} />
        </Routes>
      </MemoryRouter>
    </MockThemeProvider>
  );

  return Wrapper;
};

describe('<StartProcessFromForm />', () => {
  it('should render', () => {
    render(<StartProcessFromForm />, {
      wrapper: getWrapper({
        initialEntries: ['/new/foo'],
      }),
    });

    expect(screen.getByText('bpmnProcessId: foo')).toBeInTheDocument();
  });
});
