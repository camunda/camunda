import { FC, useEffect, useState } from "react";
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
    >
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
    </StyledCSSTransition>
  );
};

export default Notification;
