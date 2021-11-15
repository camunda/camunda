/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import * as Styled from './styled';

type Props = {
  progressPercentage: number;
};

const ProgressBar: React.FC<Props> = ({progressPercentage}) => {
  const barWidth = Math.min(Math.max(0, progressPercentage), 100);
  return (
    <Styled.Container>
      <Styled.Background />
      <Styled.Bar width={barWidth} data-testid="progress-bar" />
    </Styled.Container>
  );
};

export {ProgressBar};
