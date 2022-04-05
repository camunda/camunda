/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React, {useState, useLayoutEffect, useRef} from 'react';
import {Block, Container} from './styled';

const SKELETON_ROW_HEIGHT = 55;

const Skeleton: React.FC = () => {
  const [numberOfRows, setNumberOfRows] = useState(0);
  const skeletonRef = useRef<HTMLDivElement>(null);

  useLayoutEffect(() => {
    const {clientHeight} = skeletonRef.current?.parentNode as Element;

    if (clientHeight !== undefined) {
      setNumberOfRows(Math.floor(clientHeight / SKELETON_ROW_HEIGHT));
    }
  }, []);

  return (
    <Container data-testid="skeleton" ref={skeletonRef}>
      {[...new Array(numberOfRows)].map((_, index) => (
        <Block key={index} />
      ))}
    </Container>
  );
};

export {Skeleton};
