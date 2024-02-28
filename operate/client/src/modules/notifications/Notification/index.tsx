/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {CSSTransition} from 'react-transition-group';
import {ActionableNotification, ToastNotification} from './styled';
import {Notification as NotificationType} from 'modules/stores/notifications';
import {observer} from 'mobx-react-lite';
import {useRef} from 'react';
import {useRelativeDate} from './useRelativeDate';

type Props = {
  notification: NotificationType;
  animationTimeout: number;
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
    animationTimeout,
    ...props
  }) => {
    const nodeRef = useRef<HTMLDivElement | null>(null);
    const relativeDate = useRelativeDate(date);

    return (
      <CSSTransition
        timeout={animationTimeout}
        classNames="toast"
        nodeRef={nodeRef}
        {...props}
      >
        <div ref={nodeRef}>
          {isActionable ? (
            <ActionableNotification
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
              actionButtonLabel={actionButtonLabel ?? ''}
              onActionButtonClick={onActionButtonClick}
            />
          ) : (
            <ToastNotification
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
