/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';

import * as Styled from './styled';

type Props = {
  onStateChange?: (...args: any[]) => any;
  onClick?: (...args: any[]) => any;
};

export default class SubOption extends React.Component<Props> {
  handleOnClick = (evt: any) => {
    evt && evt.stopPropagation();
    // @ts-expect-error ts-migrate(2722) FIXME: Cannot invoke an object which is possibly 'undefin... Remove this comment to see the full error message
    this.props.onClick();
    // @ts-expect-error ts-migrate(2722) FIXME: Cannot invoke an object which is possibly 'undefin... Remove this comment to see the full error message
    this.props.onStateChange({isOpen: false});
  };

  render() {
    return (
      <Styled.OptionButton onClick={this.handleOnClick}>
        {this.props.children}
      </Styled.OptionButton>
    );
  }
}
