/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React, {useContext} from 'react';
import {NotificationContainer} from 'modules/components/NotificationContainer';

type Options = {
  headline: string;
  description?: string;
  isDismissable?: boolean;
  navigation?: any;
  showCreationTime?: boolean;
};

type Notification = {
  remove: () => void;
  hasBeenShown: () => boolean;
};

type DisplayNotificationFn = (
  appearance: 'success' | 'error' | 'info',
  options: Options
) => Promise<Notification | undefined>;

type NotificationContextType = {
  displayNotification: DisplayNotificationFn;
};

type ProviderProps = {
  children: React.ReactNode;
};

const NotificationContext = React.createContext<
  Partial<NotificationContextType>
>({});

const NotificationProvider: React.FC<ProviderProps> = ({children}) => {
  const notificationRef = React.createRef<HTMLCmNotificationContainerElement>();

  const displayNotification: DisplayNotificationFn = async (
    appearance,
    {headline, description, isDismissable, navigation, showCreationTime}
  ) => {
    return await notificationRef.current?.enqueueNotification({
      headline,
      description,
      appearance,
      userDismissable: isDismissable,
      navigation,
      showCreationTime,
    });
  };

  return (
    <NotificationContext.Provider value={{displayNotification}}>
      {children}
      <NotificationContainer ref={notificationRef} />
    </NotificationContext.Provider>
  );
};

function useNotifications() {
  return useContext(NotificationContext) as NotificationContextType;
}

export type {Notification, NotificationContextType};
export {NotificationProvider, useNotifications};
