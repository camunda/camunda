/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {Component} from 'react';
import PanelHeader from './PanelHeader';
import PanelFooter from './PanelFooter';
import PanelBody from './PanelBody';
import * as Styled from './styled';

type Props = {
  children?: React.ReactNode;
};

class Panel extends Component<Props> {
  static Footer = PanelFooter;
  static Header = PanelHeader;
  static Body = PanelBody;

  render() {
    const {children} = this.props;
    return <Styled.Panel {...this.props}>{children}</Styled.Panel>;
  }
}

export {Panel};
