/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
import styled from "styled-components";
import { FC } from "react";
import {
  ActionableNotification,
  InlineNotification as CarbonInlineNotification,
} from "@carbon/react";
import { spacing05 } from "@carbon/elements";
import useTranslate from "src/utility/localization";
import { NotificationOptions } from "./NotificationContext";

const StyledInlineNotification = styled(CarbonInlineNotification)`
  max-width: 100%;
  margin: ${spacing05} 0 ${spacing05};
`;

const StyledActionableNotification = styled(ActionableNotification)`
  max-width: 100%;
  margin: ${spacing05} 0 ${spacing05};
`;

type InlineNotificationProps = NotificationOptions & {
  actionButton?: {
    label: string;
    onClick: () => void;
  };
};

export const InlineNotification: FC<InlineNotificationProps> = ({
  kind = "info",
  title,
  role,
  actionButton,
}) => {
  const props = {
    kind,
    hideCloseButton: true,
    lowContrast: true,
    role: role ?? (kind === "error" ? "alert" : "status"),
    title,
  };

  return actionButton ? (
    <StyledActionableNotification
      {...props}
      inline
      actionButtonLabel={actionButton?.label}
      onActionButtonClick={actionButton?.onClick}
    />
  ) : (
    <StyledInlineNotification {...props} />
  );
};

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
  role,
  actionButton,
}) => (
  <InlineNotification
    kind="error"
    title={title}
    role={role}
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
