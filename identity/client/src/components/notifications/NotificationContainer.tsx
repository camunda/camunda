import styled from "styled-components";
import { spacing03 } from "@carbon/elements";
import { FC, ReactNode } from "react";
import { TransitionGroup } from "react-transition-group";

const NotificationWrapper = styled.div`
  position: fixed;
  top: 3.5rem;
  right: ${spacing03};
  z-index: 10000;
  max-width: 100%;
`;

const NotificationContainer: FC<{ children?: ReactNode }> = ({ children }) => (
  <NotificationWrapper>
    <TransitionGroup>{children}</TransitionGroup>
  </NotificationWrapper>
);

export default NotificationContainer;
