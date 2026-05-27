/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import { FC, useEffect } from "react";
import { useNotifications } from "src/components/notifications";
import useTranslate from "src/utility/localization";
import { isLoggedIn } from "src/utility/auth";
import { setErrorNotifier } from "./errorNotification";
import { ApiError, isDetailedError } from "./request";

const ErrorNotificationBridge: FC = () => {
  const { enqueueNotification } = useNotifications();
  const { t } = useTranslate("components");

  useEffect(() => {
    setErrorNotifier((error: ApiError) => {
      const { status, body } = error;

      switch (status) {
        case 401:
          if (isLoggedIn()) {
            enqueueNotification({
              kind: "error",
              title: t("unauthorized"),
              subtitle: t("sessionExpired"),
            });
          }
          return;
        case 403:
          enqueueNotification({
            kind: "error",
            title: t("forbidden"),
            subtitle: t("accessDenied"),
          });
          return;
        case 404:
          enqueueNotification({
            kind: "error",
            title: t("notFound"),
            subtitle: t("entityNotFound"),
          });
          return;
        default:
          if (body && isDetailedError(body)) {
            enqueueNotification({
              kind: "error",
              title: body.title ?? t("errorOccurred"),
              subtitle: body.detail,
            });
          } else {
            enqueueNotification({
              kind: "error",
              title: t("errorOccurred"),
              subtitle:
                (body && "error" in body ? body.error : undefined) ||
                t("tryAgainLater"),
            });
          }
      }
    });
  }, [enqueueNotification, t]);

  return null;
};

export default ErrorNotificationBridge;
