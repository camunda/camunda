/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import { FC, ReactNode } from "react";
import { Toaster, toast } from "@camunda/design-system";
import NotificationContext, {
  EnqueueNotification,
  NotificationOptions,
} from "../notifications/NotificationContext";

const TOAST_FN_BY_KIND: Record<
  NonNullable<NotificationOptions["kind"]>,
  typeof toast.info
> = {
  success: toast.success,
  error: toast.error,
  info: toast.info,
  warning: toast.warning,
};

const enqueueNotification: EnqueueNotification = ({
  kind = "info",
  title,
  subtitle,
}) => {
  const toastFn = TOAST_FN_BY_KIND[kind];
  toastFn(title, { description: subtitle });
};

const contextValue = { enqueueNotification };

const NotificationProvider: FC<{ children?: ReactNode }> = ({ children }) => (
  <NotificationContext.Provider value={contextValue}>
    <Toaster position="top-right" />
    {children}
  </NotificationContext.Provider>
);

export default NotificationProvider;
