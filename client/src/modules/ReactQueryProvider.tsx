/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {Suspense, lazy} from 'react';

const ReactQueryProviderReal = lazy(() =>
  import('./ReactQueryProviderReal').then((module) => ({
    default: module.ReactQueryProvider,
  })),
);

type Props = {
  children: React.ReactNode;
};

const ReactQueryProvider: React.FC<Props> = ({children}) => {
  if (process.env.NODE_ENV === 'development') {
    return (
      <Suspense fallback={null}>
        <ReactQueryProviderReal>{children}</ReactQueryProviderReal>
      </Suspense>
    );
  }

  return <>{children}</>;
};

export {ReactQueryProvider};
