/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {Button} from '@carbon/react';
import {Add} from '@carbon/react/icons';

type Props = {
  disabled?: boolean;
  onClick: () => void;
  className?: string;
};

const AddVariableButton: React.FC<Props> = ({
  disabled = false,
  onClick,
  className,
}) => {
  return (
    <Button
      kind="ghost"
      size="md"
      onClick={onClick}
      disabled={disabled}
      className={className}
      renderIcon={Add}
    >
      Add Variable
    </Button>
  );
};

export {AddVariableButton};
