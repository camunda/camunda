/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {ActionableNotification, ToastNotification} from '@carbon/react';
import {CSSTransition} from 'react-transition-group';
import type {Notification as NotificationType} from 'common/notifications/notifications.store';
import {observer} from 'mobx-react-lite';
import {useRef} from 'react';
import {useRelativeDate} from './useRelativeDate';
import {useTranslation} from 'react-i18next';
import styles from './styles.module.scss';

type Props = {
  notification: NotificationType;
};

const Notification: React.FC<Props> = observer(
  ({
    notification: {
      kind,
      title,
      subtitle,
      isDismissable,
      date,
      hideNotification,
      isActionable,
      actionButtonLabel,
      onActionButtonClick,
    },
    ...props
  }) => {
    const {t} = useTranslation();
    const nodeRef = useRef<HTMLDivElement | null>(null);
    const relativeDate = useRelativeDate(date);

    return (
      <CSSTransition
        timeout={300}
        classNames={{
          enter: styles.toastEnter,
          enterActive: styles.toastEnterActive,
          exitActive: styles.toastExitActive,
          exitDone: styles.toastExitDone,
        }}
        nodeRef={nodeRef}
        {...props}
      >
        <div ref={nodeRef}>
          {isActionable ? (
            <ActionableNotification
              className={styles.notification}
              kind={kind}
              lowContrast={false}
              title={title}
              subtitle={subtitle}
              hideCloseButton={!isDismissable}
              onClose={() => {
                hideNotification();
                return false;
              }}
              actionButtonLabel={
                actionButtonLabel ? t('notificationActionButtonLabel') : ''
              }
              onActionButtonClick={onActionButtonClick}
            />
          ) : (
            <ToastNotification
              className={styles.notification}
              kind={kind}
              lowContrast={false}
              title={title}
              caption={relativeDate}
              subtitle={subtitle}
              hideCloseButton={!isDismissable}
              onClose={() => {
                hideNotification();
                return false;
              }}
            />
          )}
        </div>
      </CSSTransition>
    );
  },
);

export {Notification};
