/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
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
