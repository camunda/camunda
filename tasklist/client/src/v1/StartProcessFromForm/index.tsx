/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useStartProcessParams} from 'common/routing';
import {useExternalForm} from 'v1/api/useExternalForm.query';
import {useLayoutEffect, useState} from 'react';
import {Content} from '@carbon/react';
import {FormJS} from './FormJS';
import {Skeleton} from './FormJS/Skeleton';
import {useStartExternalProcess} from 'v1/api/useStartExternalProcess.mutation';
import {useTranslation} from 'react-i18next';
import {logger} from 'common/utils/logger';
import {tracking} from 'common/tracking';
import CheckImage from 'common/images/orange-check-mark.svg';
import ErrorRobotImage from 'common/images/error-robot.svg';
import {Message} from './Message';
import {match, Pattern} from 'ts-pattern';
import styles from './styles.module.scss';
import {hasFileComponents} from './hasFileComponents';

function parseValidJSON(schema: string): null | object {
  try {
    return JSON.parse(schema);
  } catch {
    return null;
  }
}

const StartProcessFromForm: React.FC = () => {
  const [pageView, setPageView] = useState<
    | 'form'
    | 'submit-success'
    | 'form-not-found'
    | 'failed-submission'
    | 'invalid-form-schema'
    | 'schema-with-file-components'
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

  useLayoutEffect(() => {
    const parsedSchema = parseValidJSON(data?.schema ?? '');
    if (parsedSchema !== null && hasFileComponents(parsedSchema)) {
      tracking.track({
        eventName: 'public-start-form-schema-with-file-components',
      });
      setPageView('schema-with-file-components');
    }
  }, [data?.schema]);

  const {t} = useTranslation();

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
                  altText: t('publicStartFormSuccessIconAltText'),
                  path: CheckImage,
                }}
                heading={t('publicStartFormSuccessMessageTitle')}
                description={
                  <>
                    {t('formSubmittedSuccessfullyDescription')}
                    <br />
                    {t('startProcessFromFormCloseWindowInstruction')}
                  </>
                }
              />
            ))
            .with({pageView: 'form-not-found'}, () => (
              <Message
                icon={{
                  altText: t('startProcessFromFormErrorRobot'),
                  path: ErrorRobotImage,
                }}
                heading={t('startProcessFromFormPageNotFoundHeading')}
                description={t('startProcessFromFormPageNotFoundDescription')}
              />
            ))
            .with({pageView: 'failed-submission'}, () => (
              <Message
                icon={{
                  altText: t('startProcessFromFormErrorRobot'),
                  path: ErrorRobotImage,
                }}
                heading={t('startProcessFromFormSubmissionFailedHeading')}
                description={t(
                  'startProcessFromFormSubmissionFailedDescription',
                )}
                button={{
                  label: t('startProcessFromFormReloadButton'),
                  onClick: () => {
                    reset();
                  },
                }}
              />
            ))
            .with({pageView: 'invalid-form-schema'}, () => (
              <Message
                icon={{
                  altText: t('startProcessFromFormErrorRobot'),
                  path: ErrorRobotImage,
                }}
                heading={t('startProcessFromFormInvalidFormHeading')}
                description={t('startProcessFromFormInvalidFormDescription')}
              />
            ))
            .with({pageView: 'schema-with-file-components'}, () => (
              <Message
                icon={{
                  altText: t('startProcessFromFormErrorRobot'),
                  path: ErrorRobotImage,
                }}
                heading={t('startProcessFromFormWithFileComponentsHeading')}
                description={t(
                  'startProcessFromFormWithFileComponentsDescription',
                )}
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
