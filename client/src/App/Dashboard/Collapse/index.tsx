/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
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
