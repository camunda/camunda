/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {FrameContainer, FrameHeader} from './styled';

type FrameProps = {
  headerTitle: string;
  isVisible: boolean;
};

const Frame: React.FC<{frame?: FrameProps; children: React.ReactNode}> = ({
  frame,
  children,
}) => {
  if (frame === undefined) {
    return <>{children}</>;
  }

  const {isVisible, headerTitle} = frame;

  return (
    <FrameContainer $hasBorder={isVisible} data-testid="frame-container">
      {isVisible && <FrameHeader>{headerTitle}</FrameHeader>}
      {children}
    </FrameContainer>
  );
};

export {Frame};
export type {FrameProps};
