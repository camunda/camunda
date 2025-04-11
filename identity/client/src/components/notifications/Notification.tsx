/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import { FC, useEffect, useRef, useState } from "react";
import { CSSTransition } from "react-transition-group";
import styled from "styled-components";
import { ToastNotification } from "@carbon/react";
import { spacing03 } from "@carbon/elements";
import { NotificationOptions } from "./NotificationContext";

const NOTIFICATION_TIMEOUT = 5000;
const TRANSITION_DURATION = 300;

const StyledCSSTransition = styled(CSSTransition)`
  margin-bottom: ${spacing03};

  &.toast-enter {
    transform: translateX(120%);
  }

  &.toast-enter-active {
    transform: translateX(0);
    transition: transform ${TRANSITION_DURATION}ms ease-in-out;
  }

  &.toast-exit-active {
    transform: translateX(120%);
    transition: transform ${TRANSITION_DURATION}ms ease-in-out;
  }

  &.toast-exit-done {
    display: none;
  }
`;

export type NotificationProps = NotificationOptions & {
  onClose: () => void;
};

const Notification: FC<NotificationProps> = ({
  kind = "info",
  title,
  subtitle,
  role,
  onClose,
}) => {
  const [show, setShow] = useState(false);
  // https://github.com/reactjs/react-transition-group/issues/668#issuecomment-695162879
  const nodeRef = useRef(null);

  useEffect(() => {
    setShow(true);
    setTimeout(() => setShow(false), NOTIFICATION_TIMEOUT);
  }, []);

  return (
    <StyledCSSTransition
      in={show}
      timeout={TRANSITION_DURATION}
      classNames="toast"
      onExited={onClose}
      unmountOnExit
      nodeRef={nodeRef}
    >
      <div ref={nodeRef}>
        <ToastNotification
          kind={kind}
          lowContrast
          onClose={() => {
            setShow(false);
            return false;
          }}
          role={role || (kind === "error" ? "alert" : "status")}
          title={title}
          subtitle={subtitle}
        />
      </div>
    </StyledCSSTransition>
  );
};

export default Notification;
