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
import {useStartExternalProcess} from 'modules/mutations/useStartExternalProcess';
import {logger} from 'modules/utils/logger';
import {Heading} from '@carbon/react';

const StartProcessFromForm: React.FC = () => {
  const {bpmnProcessId} = useStartProcessParams();
  const {data, error} = useExternalForm(bpmnProcessId);
  const {mutateAsync: startExternalProcess, isSuccess} =
    useStartExternalProcess();

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
        <Heading className="cds--visually-hidden">Form</Heading>
        <FormContainer>
          {isSuccess ? (
            <Heading>Success</Heading>
          ) : (
            <>
              {data === undefined ? (
                <Skeleton />
              ) : (
                <FormJS
                  schema={data.schema}
                  onSubmit={async (variables) => {
                    try {
                      await startExternalProcess({variables, bpmnProcessId});
                    } catch (error) {
                      logger.error(error);
                      notificationsStore.displayNotification({
                        kind: 'error',
                        title: 'Could not submit form',
                        isDismissable: false,
                      });
                    }
                  }}
                />
              )}
            </>
          )}
        </FormContainer>
        <LogoIcon />
      </Main>
    </>
  );
};

export {StartProcessFromForm};
