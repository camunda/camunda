/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import { FC, ReactNode, useMemo, useState } from "react";
import Notification from "./Notification";
import NotificationContainer from "./NotificationContainer";
import NotificationContext, {
  EnqueueNotification,
  NotificationOptions,
} from "./NotificationContext";

const NotificationProvider: FC<{ children?: ReactNode }> = ({ children }) => {
  const [notifications, setNotifications] = useState<
    (NotificationOptions & { id: string })[]
  >([]);
  const contextValue: { enqueueNotification: EnqueueNotification } = useMemo(
    () => ({
      enqueueNotification: (notification) =>
        setNotifications((prevNotifications) => [
          { ...notification, id: (Date.now() + Math.random()).toString() },
          ...prevNotifications,
        ]),
    }),
    [],
  );

  const removeNotification = (id: string) => () => {
    setNotifications((prevNotifications) =>
      prevNotifications.filter((notification) => notification.id !== id),
    );
  };

  return (
    <NotificationContext.Provider value={contextValue}>
      <NotificationContainer>
        {notifications.map(({ id, ...notificationProps }) => (
          <Notification
            key={id}
            onClose={removeNotification(id)}
            {...notificationProps}
          />
        ))}
      </NotificationContainer>
      {children}
    </NotificationContext.Provider>
  );
};

export default NotificationProvider;
