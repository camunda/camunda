/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {FrameContainer, FrameHeader} from './styled';

type FrameProps = {
  headerTitle: string;
  isVisible?: boolean;
};

const Frame: React.FC<{frame?: FrameProps; children: React.ReactNode}> = ({
  frame,
  children,
}) => {
  if (frame === undefined) {
    return <>{children}</>;
  }

  const {isVisible = true, headerTitle} = frame;

  return (
    <FrameContainer $hasBorder={isVisible} data-testid="frame-container">
      {isVisible && <FrameHeader>{headerTitle}</FrameHeader>}
      {children}
    </FrameContainer>
  );
};

export {Frame};
export type {FrameProps};
