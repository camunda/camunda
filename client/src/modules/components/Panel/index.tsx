/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {Component} from 'react';
import PanelHeader from './PanelHeader';
import PanelFooter from './PanelFooter';
import PanelBody from './PanelBody';

import * as Styled from './styled';

class Panel extends Component<{}> {
  static Footer = PanelFooter;
  static Header = PanelHeader;
  static Body = PanelBody;

  render() {
    const {children} = this.props;
    return <Styled.Panel {...this.props}>{children}</Styled.Panel>;
  }
}

export {Panel};
