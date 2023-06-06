/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {useStartProcessParams} from 'modules/routing';
import {Content, LogoIcon, FormContainer} from './styled';
import {useExternalForm} from 'modules/queries/useExternalForm';
import {Header} from './Header';
import {useEffect} from 'react';
import {FormJS} from './FormJS';
import {Skeleton} from './Skeleton';
import {useStartExternalProcess} from 'modules/mutations/useStartExternalProcess';
import {logger} from 'modules/utils/logger';
import {tracking} from 'modules/tracking';
import {C3EmptyState} from '@camunda/camunda-composite-components';
import CheckImage from 'modules/images/orange-check-mark.svg';
import ErrorRobotImage from 'modules/images/error-robot.svg';

const StartProcessFromForm: React.FC = () => {
  const {bpmnProcessId} = useStartProcessParams();
  const {data, error, isInitialLoading} = useExternalForm(bpmnProcessId);
  const {
    mutate: startExternalProcess,
    isSuccess,
    isError,
    reset,
  } = useStartExternalProcess({
    onError: (error) => {
      logger.error(error);
      tracking.track({
        eventName: 'public-start-form-submission-failed',
      });
    },
    onMutate: () => {
      tracking.track({
        eventName: 'public-start-form-submitted',
      });
    },
  });

  useEffect(() => {
    if (error !== null) {
      tracking.track({
        eventName: 'public-start-form-load-failed',
      });
    }
  }, [error]);

  useEffect(() => {
    tracking.track({
      eventName: 'public-start-form-opened',
    });
  }, []);

  useEffect(() => {
    if (data !== undefined) {
      tracking.track({
        eventName: 'public-start-form-loaded',
      });
    }
  }, [data]);

  return (
    <>
      <Header name={data?.title ?? ''} />
      <Content id="main-content" tabIndex={-1} tagName="main">
        <FormContainer>
          {isSuccess ? (
            <C3EmptyState
              icon={{
                altText: 'Success checkmark',
                path: CheckImage,
              }}
              heading="Success!"
              description={
                <>
                  Your form has been successfully submitted.
                  <br />
                  You can close this window now.
                </>
              }
            />
          ) : (
            <>
              {isError ? (
                <C3EmptyState
                  icon={{
                    altText: 'Error robot',
                    path: ErrorRobotImage,
                  }}
                  heading="Something went wrong"
                  description="Please try again later and reload the page."
                  button={{
                    label: 'Reload',
                    onClick: () => {
                      reset();
                    },
                  }}
                />
              ) : (
                <>
                  {isInitialLoading && data === undefined ? <Skeleton /> : null}
                  {data === undefined || error !== null ? null : (
                    <FormJS
                      title={data.title}
                      schema={data.schema}
                      onSubmit={(variables) => {
                        startExternalProcess({variables, bpmnProcessId});
                      }}
                    />
                  )}
                  {error === null ? null : (
                    <C3EmptyState
                      icon={{
                        altText: 'Error robot',
                        path: ErrorRobotImage,
                      }}
                      heading="404 - Page not found"
                      description="We're sorry! The requested URL you're looking for could not be found."
                    />
                  )}
                </>
              )}
            </>
          )}
        </FormContainer>
        <LogoIcon />
      </Content>
    </>
  );
};

export {StartProcessFromForm};
