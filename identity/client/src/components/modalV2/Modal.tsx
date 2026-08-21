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
  buttons,
  size = "lg",
  passiveModal = false,
  preventCloseOnClickOutside = false,
}) => {
  const { t } = useTranslate("components");
<<<<<<< HEAD
=======

  const footer = (() => {
    if (buttons !== undefined) {
      // Carbon's `ButtonSet` has no design system counterpart, and none is
      // needed: `DialogFooter` is itself the flex row. Carbon's equal-width
      // split (`flex: 0 1 50%`, stretched to 100% here) is deliberately not
      // reproduced — the design system footer right-aligns buttons at their
      // natural width. No call site passes `buttons` today, so this branch is
      // untested surface.
      return <DialogFooter>{buttons}</DialogFooter>;
    }

    if (passiveModal) return null;

    return (
      <DialogFooter>
        <Button variant="secondary" onClick={onClose}>
          {t("cancel")}
        </Button>
        <Button
          variant={danger ? "destructive" : "default"}
          // `loading` already blocks clicks via `aria-disabled` while keeping
          // the button focusable, so it must not be folded into `disabled`.
          loading={loading}
          disabled={submitDisabled}
          onClick={() => onSubmit()}
        >
          {loading ? loadingDescription || t("loading") : confirmLabel}
        </Button>
      </DialogFooter>
    );
  })();
>>>>>>> 879d6eed (refactor: migrate global task listener page)

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
<<<<<<< HEAD
        {passiveModal ? null : (
          <DialogFooter>
            {buttons !== undefined ? (
              buttons
            ) : (
              <>
                <Button variant="secondary" onClick={onClose}>
                  {t("cancel")}
                </Button>
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
=======
        {footer}
>>>>>>> 879d6eed (refactor: migrate global task listener page)
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
