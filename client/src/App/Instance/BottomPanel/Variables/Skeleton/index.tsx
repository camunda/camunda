/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import * as Styled from './styled';
import {Rows} from './Rows';
import EmptyPanel from 'modules/components/EmptyPanel';

type Props = {
  type?: string;
  label?: string;
  rowHeight?: number;
};

const Skeleton = ({type, label, rowHeight}: Props) => {
  return (
    <Styled.Table>
      <tbody>
        <tr>
          <Styled.SkeletonTD>
            <EmptyPanel
              // @ts-expect-error ts-migrate(2322) FIXME: Type 'string' is not assignable to type '"info" | ... Remove this comment to see the full error message
              type={type}
              label={label}
              Skeleton={Rows}
              rowHeight={rowHeight}
            />
          </Styled.SkeletonTD>
        </tr>
      </tbody>
    </Styled.Table>
  );
};

export {Skeleton};
