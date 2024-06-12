/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useStartProcessParams} from 'modules/routing';
import {useExternalForm} from 'modules/queries/useExternalForm';
import {useLayoutEffect, useState} from 'react';
import {Content} from '@carbon/react';
import {FormJS} from './FormJS';
import {Skeleton} from './FormJS/Skeleton';
import {useStartExternalProcess} from 'modules/mutations/useStartExternalProcess';
import {logger} from 'modules/utils/logger';
import {tracking} from 'modules/tracking';
import CheckImage from 'modules/images/orange-check-mark.svg';
import ErrorRobotImage from 'modules/images/error-robot.svg';
import {Message} from './Message';
import {match, Pattern} from 'ts-pattern';
import styles from './styles.module.scss';

const StartProcessFromForm: React.FC = () => {
  const [pageView, setPageView] = useState<
    | 'form'
    | 'submit-success'
    | 'form-not-found'
    | 'failed-submission'
    | 'invalid-form-schema'
  >('form');
  const {bpmnProcessId} = useStartProcessParams();
  const {data, error} = useExternalForm(bpmnProcessId);
  const {mutateAsync: startExternalProcess, reset} = useStartExternalProcess({
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

  useLayoutEffect(() => {
    if (error !== null) {
      tracking.track({
        eventName: 'public-start-form-load-failed',
      });
      setPageView('form-not-found');
    }
  }, [error]);

  useLayoutEffect(() => {
    tracking.track({
      eventName: 'public-start-form-opened',
    });
  }, []);

  useLayoutEffect(() => {
    if (data !== undefined) {
      tracking.track({
        eventName: 'public-start-form-loaded',
      });
      setPageView('form');
    }
  }, [data]);

  return (
    <>
      <Content
        id="main-content"
        className={styles.content}
        tabIndex={-1}
        tagName="main"
      >
        <div className={styles.container}>
          {match({data, pageView})
            .with({pageView: 'form', data: undefined}, () => <Skeleton />)
            .with(
              {pageView: 'form', data: Pattern.not(undefined)},
              ({data}) => (
                <FormJS
                  schema={data.schema}
                  handleSubmit={async (variables) => {
                    await startExternalProcess({variables, bpmnProcessId});
                  }}
                  onImportError={() => {
                    setPageView('invalid-form-schema');
                  }}
                  onSubmitError={() => {
                    setPageView('failed-submission');
                  }}
                  onSubmitSuccess={() => {
                    setPageView('submit-success');
                  }}
                />
              ),
            )
            .with({pageView: 'submit-success'}, () => (
              <Message
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
            ))
            .with({pageView: 'form-not-found'}, () => (
              <Message
                icon={{
                  altText: 'Error robot',
                  path: ErrorRobotImage,
                }}
                heading="404 - Page not found"
                description="We're sorry! The requested URL you're looking for could not be found."
              />
            ))
            .with({pageView: 'failed-submission'}, () => (
              <Message
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
            ))
            .with({pageView: 'invalid-form-schema'}, () => (
              <Message
                icon={{
                  altText: 'Error robot',
                  path: ErrorRobotImage,
                }}
                heading="Invalid form"
                description="Something went wrong and the form could not be displayed. Please contact your provider."
              />
            ))
            .exhaustive()}
        </div>
      </Content>
    </>
  );
};

StartProcessFromForm.displayName = 'StartProcessFromForm';

export {StartProcessFromForm as Component};
