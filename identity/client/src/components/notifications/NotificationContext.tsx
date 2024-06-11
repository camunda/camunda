import React, { useContext } from "react";

export type NotificationOptions = {
  title: string;
  subtitle?: string;
  kind?: "success" | "error" | "info" | "warning";
  role?: "alert" | "log" | "status";
};

export type EnqueueNotification = (options: NotificationOptions) => void;

export type NotificationContextValue = {
  enqueueNotification: EnqueueNotification;
};

const NotificationContext = React.createContext<NotificationContextValue>({
  enqueueNotification: () => undefined,
});

export const useNotifications = () => useContext(NotificationContext);

export default NotificationContext;
