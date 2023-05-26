/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {ReactQueryProvider} from 'modules/ReactQueryProvider';
import {MockThemeProvider} from 'modules/theme/MockProvider';
import {MemoryRouter} from 'react-router-dom';

type Props = {
  children?: React.ReactNode;
};

const Wrapper: React.FC<Props> = ({children}) => (
  <ReactQueryProvider>
    <MockThemeProvider>
      <MemoryRouter initialEntries={['/']}>{children}</MemoryRouter>
    </MockThemeProvider>
  </ReactQueryProvider>
);

export {Wrapper};
