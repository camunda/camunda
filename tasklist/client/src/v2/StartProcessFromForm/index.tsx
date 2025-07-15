/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useLayoutEffect} from 'react';
import {Content} from '@carbon/react';
import {useTranslation} from 'react-i18next';
import {tracking} from 'common/tracking';
import ErrorRobotImage from 'common/images/error-robot.svg';
import {StartProcessFromFormMessage} from 'common/public-start-form/StartProcessFromFormMessage';
import styles from 'common/public-start-form/startProcessFromForm.module.scss';

const StartProcessFromForm: React.FC = () => {
  const {t} = useTranslation();

  useLayoutEffect(() => {
    tracking.track({
      eventName: 'public-start-form-v2-api-not-supported',
    });
  }, []);

  return (
    <>
      <Content
        id="main-content"
        className={styles.content}
        tabIndex={-1}
        tagName="main"
      >
        <div className={styles.container}>
          <StartProcessFromFormMessage
            icon={{
              altText: t('startProcessFromFormErrorRobot'),
              path: ErrorRobotImage,
            }}
            heading={t('startProcessFromFormV2ApiNotSupportedHeading')}
            description={t('startProcessFromFormV2ApiNotSupportedDescription')}
          />
        </div>
      </Content>
    </>
  );
};

StartProcessFromForm.displayName = 'StartProcessFromForm';

export {StartProcessFromForm as Component};
