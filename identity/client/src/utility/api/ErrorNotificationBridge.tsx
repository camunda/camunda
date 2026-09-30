/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import { FC, useEffect } from "react";
import { useNavigate } from "react-router-dom";
import { useNotifications } from "src/components/notifications";
import useTranslate from "src/utility/localization";
import { isLoggedIn, recoverThirdPartySession } from "src/utility/auth";
import { isOIDC } from "src/configuration";
import { ErrorNotifier, setErrorNotifier } from "./errorNotification";
import { isDetailedError } from "./request";

const ErrorNotificationBridge: FC = () => {
  const { enqueueNotification } = useNotifications();
  const { t } = useTranslate("components");
  const navigate = useNavigate();

  useEffect(() => {
    const notifier: ErrorNotifier = (error, { skipToast }) => {
      const { status, body } = error;
      // recoverThirdPartySession() disables the session, so capture this first.
      const wasLoggedIn = isLoggedIn();

      // Session-recovery navigation is independent of toast suppression: any
      // 401 (including the initial auth probe) should recover the session.
      // In OIDC mode there is no in-app login form to send the user to — the
      // IdP owns the session, so reload and let the server-side auth filter
      // chain re-establish it (or bounce to the IdP's own login page).
      if (status === 401 && !window.location.pathname.includes("/login")) {
        if (isOIDC) {
          recoverThirdPartySession();
        } else {
          void navigate(`/login?next=${window.location.pathname}`, {
            replace: true,
          });
        }
      }

      if (skipToast) return;

      switch (status) {
        case 401:
          if (wasLoggedIn) {
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
    };
    setErrorNotifier(notifier);
    return () => {
      setErrorNotifier(() => {});
    };
  }, [enqueueNotification, t, navigate]);

  return null;
};

export default ErrorNotificationBridge;
