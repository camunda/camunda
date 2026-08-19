/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useRef,
  useState,
} from "react";
import {
  Button,
  NotificationBell,
  NotificationsPanel,
  type NavNotification,
} from "@camunda/design-system";
import C3NotificationProvider, {
  C3NotificationContext,
} from "@camunda/camunda-composite-components/lib/esm/src/components/c3-navigation/c3-notification-provider/c3-notification-provider.js";

import useTranslate from "src/utility/localization";

/**
 * SaaS notification feed for the header. The bell and the panel are separate
 * design-system components, so the open state lives here; C3 stays as the data
 * layer (fetching, read state, dismissal, analytics) because the design system
 * ships no notification transport.
 */
const NotificationsPanelContent = () => {
  const { t } = useTranslate("navigation");
  const {
    enabled,
    isFetching,
    notifications,
    markAllAsRead,
    dismiss,
    dismissAll,
    analytics,
  } = useContext(C3NotificationContext);
  const [isOpen, setIsOpen] = useState(false);
  const [unreadAtOpen, setUnreadAtOpen] = useState<Set<string>>(
    () => new Set(),
  );
  const unreadNotifications = useMemo(
    () => notifications.filter(({ state }) => state === "new"),
    [notifications],
  );
  const isOpenRef = useRef(isOpen);
  const notificationsRef = useRef(notifications);
  const markAllAsReadRef = useRef(markAllAsRead);

  useEffect(() => {
    isOpenRef.current = isOpen;
    notificationsRef.current = notifications;
    markAllAsReadRef.current = markAllAsRead;
  });

  useEffect(
    () => () => {
      if (isOpenRef.current) {
        markAllAsReadRef.current(
          notificationsRef.current.filter(({ state }) => state === "new"),
        );
      }
    },
    [],
  );

  const navNotifications = useMemo<NavNotification[]>(
    () =>
      [...notifications]
        .sort((left, right) => right.timestamp - left.timestamp)
        .map((notification) => ({
          key: notification.uuid,
          title: notification.title,
          description: notification.description,
          timestamp: new Intl.DateTimeFormat(undefined, {
            dateStyle: "medium",
            timeStyle: "short",
          }).format(notification.timestamp),
          isRead:
            notification.state !== "new" &&
            !unreadAtOpen.has(notification.uuid),
          href: notification.meta?.href,
          onClick:
            notification.meta?.identifier === undefined
              ? undefined
              : () => {
                  analytics(
                    "notification-clicked-cta",
                    notification.meta?.identifier,
                  );
                },
        })),
    [analytics, notifications, unreadAtOpen],
  );

  const handleOpenChange = useCallback(
    (open: boolean) => {
      setIsOpen(open);

      if (!open) {
        if (unreadNotifications.length > 0) {
          markAllAsRead(unreadNotifications);
        }
        setUnreadAtOpen(new Set());
        return;
      }

      if (unreadNotifications.length > 0) {
        setUnreadAtOpen(new Set(unreadNotifications.map(({ uuid }) => uuid)));
        markAllAsRead(unreadNotifications);
      }
      if (enabled) {
        analytics("notification-panel-opened");
      }
    },
    [analytics, enabled, markAllAsRead, unreadNotifications],
  );

  return (
    <>
      <NotificationBell
        label={t("notifications")}
        unreadCount={isFetching ? 0 : unreadNotifications.length}
        isActive={isOpen}
        onClick={() => handleOpenChange(!isOpen)}
      />
      <NotificationsPanel
        title={t("notifications")}
        notifications={navNotifications}
        open={isOpen}
        onOpenChange={handleOpenChange}
        onDismiss={(key) => {
          const notification = notifications.find(({ uuid }) => uuid === key);
          if (notification !== undefined) {
            dismiss(notification);
          }
        }}
        emptyMessage={t("notificationsEmptyDescription")}
        headerAction={
          notifications.length > 0 ? (
            <Button
              type="button"
              variant="ghost"
              size="sm"
              onClick={() => dismissAll(notifications)}
            >
              {t("notificationsDismissAll")}
            </Button>
          ) : undefined
        }
      />
    </>
  );
};

const Notifications = () => (
  <C3NotificationProvider>
    <NotificationsPanelContent />
  </C3NotificationProvider>
);

export { Notifications };
