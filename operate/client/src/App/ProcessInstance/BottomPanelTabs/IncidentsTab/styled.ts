/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import styled from 'styled-components';

const ERROR_MESSAGE_MIN_WIDTH = '8rem';

const Container = styled.section`
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  overflow: hidden;

  // The table keeps Carbon's automatic layout, so every column stays as wide as
  // its content. An error message has no natural width, so this column takes
  // the space the others leave instead: 'width: 100%' claims the remainder,
  // while 'max-width: 0' keeps its content out of the table's minimum width, so
  // a stack trace cannot push the columns to its right off the panel.
  td[data-column-key='errorMessage'],
  th.sortable-table-column-errorMessage {
    width: 100%;
    max-width: 0;
    min-width: ${ERROR_MESSAGE_MIN_WIDTH};
  }

  td[data-column-key='errorMessage'] > span {
    display: block;
    overflow: hidden;
    text-overflow: ellipsis;
  }

  // SortableTable drops Carbon's horizontal scrollbar, which silently clips the
  // rightmost columns once the table no longer fits. Restore it on the element
  // that already scrolls vertically, so the table keeps a single scroll
  // container: setting overflow-x on an inner element would force that
  // element's overflow-y to 'auto' too and nest a second scroller inside it.
  && [data-testid='data-table-container'] {
    overflow-x: auto;
  }
`;

export {Container};
