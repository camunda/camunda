/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {ThemeProvider} from 'modules/theme/ThemeProvider';
import {Form} from 'react-final-form';

const getWrapper = (initialValues?: {[key: string]: string}) => {
  const Wrapper: React.FC<{children?: React.ReactNode}> = ({children}) => {
    return (
      <ThemeProvider>
        <Form onSubmit={() => {}} initialValues={initialValues}>
          {() => children}
        </Form>
        <div>Outside element</div>
      </ThemeProvider>
    );
  };

  return Wrapper;
};

export {getWrapper};
