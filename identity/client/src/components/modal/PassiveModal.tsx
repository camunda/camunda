/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import { FC } from "react";
import { Modal, Stack } from "@carbon/react";
import { spacing06 } from "@carbon/elements";
import { ModalProps } from "./Modal";

type PassiveModalProps = Pick<
  ModalProps,
  "open" | "onClose" | "headline" | "children" | "size"
> & {
  preventCloseOnClickOutside?: boolean;
};

const PassiveModal: FC<PassiveModalProps> = ({
  children,
  open,
  onClose,
  headline,
  size,
  ...modalProps
}) => {
  return (
    <Modal
      passiveModal
      size={size}
      modalHeading={headline}
      aria-label={headline}
      open={open}
      onRequestClose={onClose}
      {...modalProps}
    >
      <Stack gap={spacing06}>{children}</Stack>
    </Modal>
  );
};

export default PassiveModal;
