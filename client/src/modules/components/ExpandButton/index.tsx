/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React from 'react';
import * as Styled from './styled';
import IconButton from 'modules/components/IconButton';

type Props = {
  isExpanded: boolean;
  children?: React.ReactNode;
  onClick: () => void;
  title?: string;
  iconButtonTheme: string;
};

const ExpandButton = React.forwardRef<any, Props>(function ExpandButton(
  {children, isExpanded, ...props},
  ref
) {
  const renderIcon = () => (
    <Styled.Transition timeout={400} in={isExpanded} appear>
      <Styled.ArrowIcon data-testid="arrow-icon" />
    </Styled.Transition>
  );

  return (
    // @ts-expect-error ts-migrate(2741) FIXME: Property 'iconButtonTheme' is missing in type '{ c... Remove this comment to see the full error message
    <IconButton {...props} icon={renderIcon()}>
      {children}
    </IconButton>
  );
});

ExpandButton.defaultProps = {
  isExpanded: false,
};

export default ExpandButton;
