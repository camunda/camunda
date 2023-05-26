/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {useStartProcessParams} from 'modules/routing';
import {Main, LogoIcon, FormContainer} from './styled';
import {useExternalForm} from 'modules/queries/useExternalForm';
import {Header} from './Header';
import {useEffect} from 'react';
import {notificationsStore} from 'modules/stores/notifications';
import {FormJS} from './FormJS';
import {Skeleton} from './Skeleton';

const StartProcessFromForm: React.FC = () => {
  const {bpmnProcessId} = useStartProcessParams();
  const {data, error} = useExternalForm(bpmnProcessId);

  useEffect(() => {
    if (error !== null) {
      notificationsStore.displayNotification({
        kind: 'error',
        title: 'Could not fetch form',
        isDismissable: false,
      });
    }
  }, [error]);

  return (
    <>
      <Header />
      <Main id="main-content" className="cds--content" tabIndex={-1}>
        <FormContainer>
          {data === undefined ? <Skeleton /> : <FormJS schema={data.schema} />}
        </FormContainer>
        <LogoIcon />
      </Main>
    </>
  );
};

export {StartProcessFromForm};
