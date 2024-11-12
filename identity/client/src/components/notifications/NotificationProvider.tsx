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
