/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React, {useState} from 'react';
import {Container, ExpandButton} from './styled';

type Props = {
  content?: React.ReactNode;
  header?: React.ReactNode;
  buttonTitle?: string;
};

function Collapse({buttonTitle, content, header}: Props) {
  const [isCollapsed, setIsCollapsed] = useState(true);

  return (
    <Container>
      <ExpandButton
        // @ts-expect-error ts-migrate(2769) FIXME: Property 'onClick' does not exist on type 'Intrins... Remove this comment to see the full error message
        onClick={() => {
          setIsCollapsed((isCollapsed) => !isCollapsed);
        }}
        title={buttonTitle}
        isExpanded={!isCollapsed}
        iconButtonTheme="default"
      />
      {header}
      {!isCollapsed && content}
    </Container>
  );
}

export {Collapse};
