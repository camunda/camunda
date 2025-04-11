/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

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
