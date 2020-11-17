/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {NotificationContainer} from 'modules/components/NotificationContainer';

type Options = {
  headline: string;
  description?: string;
  isDismissable?: boolean;
};

type ContextProps = {
  displayNotification: DisplayNotificationFn;
};

type DisplayNotificationFn = (
  appearance: 'success' | 'error' | 'info',
  options: Options,
) => void;

type ProviderProps = {
  children: React.ReactNode;
};

const NotificationContext = React.createContext<ContextProps>({
  displayNotification() {},
});

const NotificationProvider: React.FC<ProviderProps> = ({children}) => {
  const notificationRef = React.createRef<HTMLCmNotificationContainerElement>();

  const displayNotification: DisplayNotificationFn = (
    appearance,
    {headline, description, isDismissable},
  ) => {
    notificationRef?.current?.enqueueNotification({
      headline,
      description,
      appearance,
      userDismissable: isDismissable,
    });
  };

  return (
    <NotificationContext.Provider value={{displayNotification}}>
      {children}
      <NotificationContainer ref={notificationRef} />
    </NotificationContext.Provider>
  );
};

export {NotificationProvider, NotificationContext};
