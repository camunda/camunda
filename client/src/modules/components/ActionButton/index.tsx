/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {Button} from './styled';

type Props = {
  disabled?: boolean;
  onClick: () => void;
  icon: React.ReactNode;
  title: string;
  'data-testid'?: string;
};

const ActionButton: React.FC<Props> = ({
  disabled = false,
  onClick,
  icon,
  title,
  ...props
}) => {
  return (
    <Button
      type="button"
      title={title}
      onClick={onClick}
      size="large"
      disabled={disabled}
      icon={icon}
      data-testid={props['data-testid']}
    />
  );
};

export {ActionButton};
