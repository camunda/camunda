/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {Button, Plus} from './styled';

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
      type="button"
      title="Add variable"
      size="small"
      onClick={onClick}
      disabled={disabled}
      className={className}
    >
      <Plus /> Add Variable
    </Button>
  );
};

export {AddVariableButton};
