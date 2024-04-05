/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {FallbackProps} from 'react-error-boundary';
import {useRouteError} from 'react-router-dom';
import {SomethingWentWrong} from 'modules/components/Errors/SomethingWentWrong';
import {styled} from 'styled-components';

const PageContainer = styled.main`
  --cds-layer: #f4f4f4;
  --cds-text-primary: #262626;
  --cds-background: #ffffff;
  --cds-spacing-08: 2.5rem;
  --cds-spacing-12: 6rem;
  padding: var(--cds-spacing-08);
  background: var(--cds-background);
  min-height: 100vh;
  min-height: 100dvh;
  display: flex;
  align-items: center;
  justify-content: center;
`;

const ErrorWithinLayout: React.FC = () => {
  const error = useRouteError();
  console.error(error);

  return <SomethingWentWrong />;
};

const FallbackErrorPage: React.FC<FallbackProps> = ({error}) => {
  console.error(error);
  return (
    <PageContainer>
      <SomethingWentWrong />
    </PageContainer>
  );
};

export {ErrorWithinLayout, FallbackErrorPage};
