/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import * as Styled from './styled';
import {Rows} from './Rows';
import EmptyPanel from 'modules/components/EmptyPanel';
import {PropTypes} from 'prop-types';
import {TH, TR} from '../VariablesTable';

const Skeleton = ({type, label, rowHeight}) => {
  return (
    <Styled.Table>
      <Styled.THead>
        <TR>
          <TH>Variable</TH>
          <TH>Value</TH>
          <TH />
        </TR>
      </Styled.THead>
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

Skeleton.propTypes = {
  type: PropTypes.string,
  label: PropTypes.string,
  rowHeight: PropTypes.number,
};

export {Skeleton};
