/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {Transition, ArrowIcon} from './styled';
import {IconButton} from 'modules/components/IconButton';

type Props = {
  isExpanded: boolean;
  children?: React.ReactNode;
  onClick: () => void;
  title?: string;
  iconButtonTheme?: 'default' | 'foldable';
};

const ExpandButton: React.FC<Props> = ({children, isExpanded, ...props}) => {
  return (
    <IconButton
      {...props}
      icon={
        <Transition timeout={400} in={isExpanded} appear>
          <ArrowIcon data-testid="arrow-icon" />
        </Transition>
      }
    >
      {children}
    </IconButton>
  );
};

ExpandButton.defaultProps = {
  isExpanded: false,
};

export default ExpandButton;
