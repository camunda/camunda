/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React, {useContext, useRef, useState} from 'react';
import {NotificationContainer} from 'modules/components/NotificationContainer';
import {NotificationItem} from '@camunda-cloud/common-ui';

type Options = {
  headline: NotificationItem['headline'];
  description?: NotificationItem['description'];
  isDismissable?: NotificationItem['userDismissable'];
  navigation?: NotificationItem['navigation'];
  isGseNotification?: boolean;
};

type Notification = {
  remove: () => void;
  hasBeenShown: () => boolean;
};

type DisplayNotificationFn = (
  appearance: 'success' | 'error' | 'info',
  options: Options,
) => Promise<Notification | undefined>;

type NotificationContextType = {
  displayNotification: DisplayNotificationFn;
  isGseNotificationVisible: boolean;
};

type ProviderProps = {
  children: React.ReactNode;
};

const NotificationContext = React.createContext<
  Partial<NotificationContextType>
>({});

const NotificationProvider: React.FC<ProviderProps> = ({children}) => {
  const notificationRef = useRef() as React.MutableRefObject<HTMLCmNotificationContainerElement>;
  const [isGseNotificationVisible, setIsGseNotificationVisible] = useState(
    false,
  );

  const displayNotification: DisplayNotificationFn = async (
    appearance,
    {headline, description, isDismissable, navigation, isGseNotification},
  ) => {
    if (isGseNotification) {
      setIsGseNotificationVisible(true);
    }

    return await notificationRef.current?.enqueueNotification({
      headline,
      description,
      appearance,
      userDismissable: isDismissable,
      navigation,
    });
  };

  return (
    <NotificationContext.Provider
      value={{displayNotification, isGseNotificationVisible}}
    >
      {children}
      <NotificationContainer ref={notificationRef} />
    </NotificationContext.Provider>
  );
};

function useNotifications() {
  return useContext(NotificationContext) as NotificationContextType;
}

export type {Notification};
export {NotificationProvider, NotificationContext, useNotifications};
