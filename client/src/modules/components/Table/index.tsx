/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React from 'react';

import * as Styled from './styled';

class Table extends React.Component<{}> {
  static THead: any;
  static TBody: any;
  static TH: any;
  static TR: any;
  static TD: any;

  render() {
    return <Styled.Table {...this.props} />;
  }
}

Table.THead = function THead(props: any) {
  return <Styled.THead {...props} />;
};

Table.TBody = function TBody(props: any) {
  return <tbody {...props} />;
};

Table.TH = function TH(props: any) {
  return <Styled.TH {...props} />;
};

Table.TR = function TR(props: any) {
  return <Styled.TR {...props} />;
};

Table.TD = function TD(props: any) {
  return <Styled.TD {...props} />;
};

export default Table;
