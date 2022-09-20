/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {ArrowPopover} from './ArrowPopover';
import {DraggablePopover} from './DraggablePopover';

type ArrowProps = {
  variant: 'arrow';
} & React.ComponentProps<typeof ArrowPopover>;
type DraggableProps = {
  variant: 'draggable';
} & React.ComponentProps<typeof DraggablePopover>;
type Props = ArrowProps | DraggableProps;

const Popover: React.FC<Props> = (props) => {
  if (props.variant === 'arrow') {
    const {variant, ...componentProps} = props;

    return <ArrowPopover {...componentProps} />;
  }

  if (props.variant === 'draggable') {
    const {variant, ...componentProps} = props;

    return <DraggablePopover {...componentProps} />;
  }

  return null;
};

export {Popover};
