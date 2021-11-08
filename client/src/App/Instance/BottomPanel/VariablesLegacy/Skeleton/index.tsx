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
  type: 'info' | 'warning' | 'skeleton';
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
