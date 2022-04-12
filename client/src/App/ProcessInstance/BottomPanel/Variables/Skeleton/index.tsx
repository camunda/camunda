/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
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

const Skeleton: React.FC<Props> = ({type, label, rowHeight}) => {
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
