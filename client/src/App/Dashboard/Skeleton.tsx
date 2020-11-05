/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import * as Styled from './styled';
import MultiRow from 'modules/components/MultiRow';
import EmptyPanel from 'modules/components/EmptyPanel';
import {MESSAGES} from './constants';

const MultiRowContainer = (props: any) => {
  return (
    <Styled.MultiRowContainer data-testid="skeleton">
      <MultiRow Component={Styled.Block} {...props} />
    </Styled.MultiRowContainer>
  );
};

type SkeletonProps = {
  data?: any[];
  isFailed?: boolean;
  isLoaded?: boolean;
  errorType?: string;
};

const Skeleton = ({data, isFailed, isLoaded, errorType}: SkeletonProps) => {
  let label, type, placeholder, rowHeight;

  if (isFailed) {
    type = 'warning';
    // @ts-expect-error ts-migrate(2538) FIXME: Type 'undefined' cannot be used as an index type.
    label = MESSAGES[errorType].error;
  } else if (!isLoaded) {
    type = 'skeleton';
    placeholder = MultiRowContainer;
    rowHeight = 55;
    // @ts-expect-error ts-migrate(2532) FIXME: Object is possibly 'undefined'.
  } else if (data.length === 0) {
    type = 'info';
    // @ts-expect-error ts-migrate(2538) FIXME: Type 'undefined' cannot be used as an index type.
    label = MESSAGES[errorType].noData;
  }
  return (
    <EmptyPanel
      // @ts-expect-error ts-migrate(2322) FIXME: Type 'string' is not assignable to type '"warning"... Remove this comment to see the full error message
      type={type}
      label={label}
      // @ts-expect-error ts-migrate(2322) FIXME: Type '(props: any) => Element' is missing the foll... Remove this comment to see the full error message
      Skeleton={placeholder}
      rowHeight={rowHeight}
    />
  );
};

export {Skeleton};
