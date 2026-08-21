/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import { FC } from "react";
import { Alert, Button } from "@camunda/design-system";
import type { AlertVariant } from "@camunda/design-system";
import useTranslate from "src/utility/localization";
import type { NotificationOptions } from "../notifications/NotificationContext";

const VARIANT_BY_KIND: Record<
  NonNullable<NotificationOptions["kind"]>,
  AlertVariant
> = {
  error: "destructive",
  warning: "warning",
  success: "success",
  info: "info",
};

type InlineNotificationProps = NotificationOptions & {
  actionButton?: {
    label: string;
    onClick: () => void;
  };
};

export const InlineNotification: FC<InlineNotificationProps> = ({
  kind = "info",
  title,
  subtitle,
  actionButton,
}) => (
  <Alert
    variant={VARIANT_BY_KIND[kind]}
    title={title}
    description={subtitle}
    className="my-4 max-w-full"
  >
    {actionButton && (
      <Button type="button" variant="ghost" onClick={actionButton.onClick}>
        {actionButton.label}
      </Button>
    )}
  </Alert>
);

export const TranslatedInlineNotification: FC<InlineNotificationProps> = ({
  title,
  actionButton,
  ...notificationProps
}) => {
  const { t } = useTranslate();

  return (
    <InlineNotification
      title={t(title)}
      actionButton={
        actionButton && { ...actionButton, label: t(actionButton.label) }
      }
      {...notificationProps}
    />
  );
};

type ErrorInlineNotificationProps = Omit<InlineNotificationProps, "kind">;

export const ErrorInlineNotification: FC<ErrorInlineNotificationProps> = ({
  title,
  subtitle,
  actionButton,
}) => (
  <InlineNotification
    kind="error"
    title={title}
    subtitle={subtitle}
    actionButton={actionButton}
  />
);

export const TranslatedErrorInlineNotification: FC<
  Omit<InlineNotificationProps, "kind">
> = ({ title, actionButton, ...messageProps }) => {
  const { t } = useTranslate();

  return (
    <ErrorInlineNotification
      title={t(title)}
      actionButton={
        actionButton && { ...actionButton, label: t(actionButton.label) }
      }
      {...messageProps}
    />
  );
};

export default InlineNotification;
