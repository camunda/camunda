/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {Button} from './styled';

type Props = {
  isDisabled?: boolean;
  onClick: () => void;
  icon: React.ReactNode;
  title: string;
  dataTestId?: string;
};

const ActionButton: React.FC<Props> = ({
  isDisabled = false,
  onClick,
  icon,
  title,
  dataTestId,
}) => {
  return (
    <Button
      type="button"
      title={title}
      onClick={onClick}
      size="large"
      iconButtonTheme="default"
      disabled={isDisabled}
      icon={icon}
      data-testid={dataTestId}
    />
  );
};

export {ActionButton};
