/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React, {useState} from 'react';
import PropTypes from 'prop-types';
import {Container, ExpandButton} from './styled';

function Collapse({buttonTitle, content, header}) {
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

Collapse.propTypes = {
  content: PropTypes.node,
  header: PropTypes.node,
  buttonTitle: PropTypes.string,
};

export {Collapse};
