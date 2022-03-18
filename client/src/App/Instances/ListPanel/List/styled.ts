/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import styled from 'styled-components';
import {SpinnerSkeleton} from 'modules/components/SpinnerSkeleton';

const List = styled.div`
  flex-grow: 1;
  position: relative;
  overflow: auto;
`;

const TableContainer = styled.div`
  position: absolute;
  opacity: 0.9;
  height: 100%;
  width: 100%;
  left: 0;
  top: 0;
`;

const Spinner = styled(SpinnerSkeleton)`
  margin-top: 37px;
`;

export {List, TableContainer, Spinner};
