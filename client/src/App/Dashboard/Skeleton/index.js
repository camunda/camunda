/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React, {useState, useLayoutEffect, useRef} from 'react';
import {Block, Container} from './styled';

const SKELETON_ROW_HEIGHT = 55;

function Skeleton() {
  const [numberOfRows, setNumberOfRows] = useState(0);
  const skeletonRef = useRef(null);

  useLayoutEffect(() => {
    const element = skeletonRef.current;

    if (element !== null) {
      setNumberOfRows(
        Math.floor(element.parentNode.clientHeight / SKELETON_ROW_HEIGHT)
      );
    }
  }, []);

  return (
    <Container data-testid="skeleton" ref={skeletonRef}>
      {[...new Array(numberOfRows)].map((_, index) => (
        <Block key={index} />
      ))}
    </Container>
  );
}

export {Skeleton};
