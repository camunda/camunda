/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import { ComponentProps, FC, ReactNode } from "react";
import {
  Button,
  Dialog,
  DialogBody,
  DialogContent,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@camunda/design-system";
import useTranslate from "src/utility/localization";

type BaseModalProps = {
  open: boolean;
  headline: string;
  onClose: () => void;
  children?: ReactNode;
  danger?: boolean;
  onSubmit?: () => unknown;
  submitDisabled?: boolean;
  loading?: boolean;
  loadingDescription?: string | null;
  hideCancelButton?: boolean;
  buttons?: ReactNode[];
  /** Semantic size token from the Camunda design system. */
  size?: ComponentProps<typeof DialogContent>["size"];
  preventCloseOnClickOutside?: boolean;
};

// Confirm label is optional here since it will be ignored
type PassiveModalProps = BaseModalProps & {
  passiveModal: true;
  confirmLabel?: string;
};

// Confirm label is required for an active modal
type ActiveModalProps = BaseModalProps & {
  passiveModal?: false;
  confirmLabel: string;
};

export type ModalProps = PassiveModalProps | ActiveModalProps;

const Modal: FC<ModalProps> = ({
  children,
  open,
  onClose,
  headline,
  confirmLabel,
  danger = false,
  onSubmit = () => undefined,
  submitDisabled = false,
  loading = false,
  loadingDescription,
  hideCancelButton = false,
  buttons,
  size = "lg",
  passiveModal = false,
  preventCloseOnClickOutside = false,
}) => {
  const { t } = useTranslate("components");

  return (
    <Dialog
      open={open}
      onOpenChange={(nextOpen) => {
        if (!nextOpen) onClose();
      }}
    >
      <DialogContent
        size={size}
        onPointerDownOutside={
          preventCloseOnClickOutside ? (e) => e.preventDefault() : undefined
        }
      >
        <DialogHeader>
          <DialogTitle>{headline}</DialogTitle>
        </DialogHeader>
        <DialogBody>{children}</DialogBody>
        {passiveModal ? null : (
          <DialogFooter>
            {buttons !== undefined ? (
              buttons
            ) : (
              <>
                {!hideCancelButton && (
                  <Button variant="secondary" onClick={onClose}>
                    {t("cancel")}
                  </Button>
                )}
                <Button
                  variant={danger ? "destructive" : "default"}
                  loading={loading}
                  disabled={submitDisabled}
                  onClick={() => onSubmit()}
                >
                  {loading ? loadingDescription || t("loading") : confirmLabel}
                </Button>
              </>
            )}
          </DialogFooter>
        )}
      </DialogContent>
    </Dialog>
  );
};

export const DeleteModal: FC<
  Omit<ModalProps, "confirmLabel" | "buttons" | "size"> &
    Partial<Pick<ModalProps, "confirmLabel">>
> = ({ children, ...modalProps }) => {
  const { t } = useTranslate("components");

  return (
    <Modal confirmLabel={t("delete")} {...modalProps} danger size="md">
      {children}
    </Modal>
  );
};

export default Modal;
