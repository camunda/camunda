/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 *
 * Carbon's monolithic Modal owns its header/title/footer through string props,
 * while shadcn Dialog is a compound primitive. The Modal adapter expands those
 * props into the compound structure internally; ComposedModal/ModalHeader/
 * ModalBody/ModalFooter wrap their shadcn counterparts so consumers using the
 * compound Carbon API keep working.
 *
 * Modal with `danger=true` renders shadcn AlertDialog instead of Dialog, per
 * MAPPING.md — destructive confirmation flows are semantically alertdialog.
 */

import * as React from 'react';

import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
} from '../alert-dialog/alert-dialog.shadcn';
import {Button} from '../button/button.shadcn';
import {
  Dialog,
  DialogContent,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '../dialog/dialog.shadcn';

import {cn, warnDroppedProps} from '../../../lib/utils';

import type {
  ComposedModalProps as CarbonComposedModalProps,
  ModalFooterProps as CarbonModalFooterProps,
  ModalHeaderProps as CarbonModalHeaderProps,
  ModalProps as CarbonModalProps,
} from '@carbon/react';

export type ModalProps = CarbonModalProps;
export type ComposedModalProps = CarbonComposedModalProps;
export type ModalHeaderProps = CarbonModalHeaderProps;
export type ModalFooterProps = CarbonModalFooterProps;

function bridgeOnOpenChange(
  onClose:
    | ((event: React.MouseEvent) => boolean)
    | ((event: React.MouseEvent) => void)
    | React.ReactEventHandler<HTMLElement>
    | undefined,
) {
  if (!onClose) return undefined;
  return (open: boolean) => {
    if (open) return;
    (onClose as (event: React.MouseEvent) => void)(
      {} as React.MouseEvent<HTMLElement>,
    );
  };
}

function Modal(props: ModalProps) {
  const {
    alert,
    children,
    className,
    closeButtonLabel,
    danger,
    decorator,
    hasScrollingContent,
    id,
    isFullWidth,
    launcherButtonRef,
    loadingDescription,
    loadingIconDescription,
    loadingStatus,
    modalAriaLabel,
    modalHeading,
    modalLabel,
    onLoadingSuccess,
    onRequestClose,
    onRequestSubmit,
    onSecondarySubmit,
    open,
    passiveModal,
    preventCloseOnClickOutside,
    primaryButtonDisabled,
    primaryButtonText,
    secondaryButtonText,
    secondaryButtons,
    selectorPrimaryFocus,
    selectorsFloatingMenus,
    shouldSubmitOnEnter,
    size,
    slug,
    'aria-label': ariaLabel,
    ...rest
  } = props;

  warnDroppedProps('Modal', {
    alert,
    closeButtonLabel,
    decorator,
    hasScrollingContent,
    isFullWidth,
    launcherButtonRef,
    loadingDescription,
    loadingIconDescription,
    loadingStatus,
    onLoadingSuccess,
    preventCloseOnClickOutside,
    secondaryButtons,
    selectorPrimaryFocus,
    selectorsFloatingMenus,
    shouldSubmitOnEnter,
    size,
    slug,
  });

  const onOpenChange = bridgeOnOpenChange(onRequestClose);
  const handleSecondary = onSecondarySubmit ?? onRequestClose;

  if (danger) {
    return (
      <AlertDialog open={open} onOpenChange={onOpenChange}>
        <AlertDialogContent
          id={id}
          aria-label={ariaLabel ?? modalAriaLabel}
          className={className}
          {...(rest as React.ComponentProps<typeof AlertDialogContent>)}
        >
          {(modalLabel || modalHeading) && (
            <AlertDialogHeader>
              {modalLabel ? (
                <span className="text-sm text-muted-foreground">
                  {modalLabel}
                </span>
              ) : null}
              {modalHeading ? (
                <AlertDialogTitle>{modalHeading}</AlertDialogTitle>
              ) : (
                <AlertDialogTitle className="sr-only">
                  {ariaLabel ?? modalAriaLabel ?? 'Confirm action'}
                </AlertDialogTitle>
              )}
            </AlertDialogHeader>
          )}
          {children ? (
            <AlertDialogDescription asChild>
              <div>{children}</div>
            </AlertDialogDescription>
          ) : null}
          {!passiveModal &&
          (primaryButtonText !== undefined ||
            secondaryButtonText !== undefined) ? (
            <AlertDialogFooter>
              {secondaryButtonText !== undefined ? (
                <AlertDialogCancel
                  variant="outline"
                  onClick={
                    handleSecondary as React.MouseEventHandler<HTMLButtonElement>
                  }
                >
                  {secondaryButtonText}
                </AlertDialogCancel>
              ) : null}
              {primaryButtonText !== undefined ? (
                <AlertDialogAction
                  variant="destructive"
                  disabled={primaryButtonDisabled}
                  onClick={
                    onRequestSubmit as React.MouseEventHandler<HTMLButtonElement>
                  }
                >
                  {primaryButtonText}
                </AlertDialogAction>
              ) : null}
            </AlertDialogFooter>
          ) : null}
        </AlertDialogContent>
      </AlertDialog>
    );
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent
        id={id}
        aria-label={ariaLabel ?? modalAriaLabel}
        className={className}
        {...(rest as React.ComponentProps<typeof DialogContent>)}
      >
        {(modalLabel || modalHeading) && (
          <DialogHeader>
            {modalLabel ? (
              <span className="text-sm text-muted-foreground">
                {modalLabel}
              </span>
            ) : null}
            {modalHeading ? <DialogTitle>{modalHeading}</DialogTitle> : null}
          </DialogHeader>
        )}
        {children ? <div>{children}</div> : null}
        {!passiveModal &&
        (primaryButtonText !== undefined ||
          secondaryButtonText !== undefined) ? (
          <DialogFooter>
            {secondaryButtonText !== undefined ? (
              <Button
                variant="outline"
                onClick={
                  handleSecondary as React.MouseEventHandler<HTMLButtonElement>
                }
              >
                {secondaryButtonText}
              </Button>
            ) : null}
            {primaryButtonText !== undefined ? (
              <Button
                variant="default"
                disabled={primaryButtonDisabled}
                onClick={
                  onRequestSubmit as React.MouseEventHandler<HTMLButtonElement>
                }
              >
                {primaryButtonText}
              </Button>
            ) : null}
          </DialogFooter>
        ) : null}
      </DialogContent>
    </Dialog>
  );
}

function ComposedModal(props: ComposedModalProps) {
  const {
    children,
    className,
    containerClassName,
    danger,
    decorator,
    isFullWidth,
    launcherButtonRef,
    onClose,
    onKeyDown,
    open,
    preventCloseOnClickOutside,
    selectorPrimaryFocus,
    selectorsFloatingMenus,
    size,
    slug,
    'aria-label': ariaLabel,
    'aria-labelledby': ariaLabelledBy,
    ...rest
  } = props;

  warnDroppedProps('ComposedModal', {
    danger,
    decorator,
    isFullWidth,
    launcherButtonRef,
    onKeyDown,
    preventCloseOnClickOutside,
    selectorPrimaryFocus,
    selectorsFloatingMenus,
    size,
    slug,
  });

  const onOpenChange = bridgeOnOpenChange(onClose);

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent
        aria-label={ariaLabel}
        aria-labelledby={ariaLabelledBy}
        className={cn(containerClassName, className)}
        {...(rest as React.ComponentProps<typeof DialogContent>)}
      >
        {children}
      </DialogContent>
    </Dialog>
  );
}

function ModalHeader(props: ModalHeaderProps) {
  const {
    buttonOnClick,
    children,
    className,
    closeClassName,
    closeIconClassName,
    closeModal,
    iconDescription,
    label,
    labelClassName,
    title,
    titleClassName,
    ...rest
  } = props;

  warnDroppedProps('ModalHeader', {
    buttonOnClick,
    closeClassName,
    closeIconClassName,
    closeModal,
    iconDescription,
  });

  return (
    <DialogHeader className={className} {...rest}>
      {label !== undefined ? (
        <span
          className={cn('text-sm text-muted-foreground', labelClassName)}
        >
          {label}
        </span>
      ) : null}
      {title !== undefined ? (
        <DialogTitle className={titleClassName}>{title}</DialogTitle>
      ) : null}
      {children}
    </DialogHeader>
  );
}

type ModalBodyOwnProps = {
  children?: React.ReactNode;
  className?: string;
  hasForm?: boolean;
  hasScrollingContent?: boolean;
};

function ModalBody(
  props: ModalBodyOwnProps & React.HTMLAttributes<HTMLDivElement>,
) {
  const {children, className, hasForm, hasScrollingContent, ...rest} = props;

  warnDroppedProps('ModalBody', {hasForm, hasScrollingContent});

  return (
    <div className={cn('grid gap-4', className)} {...rest}>
      {children}
    </div>
  );
}

function ModalFooter(props: ModalFooterProps) {
  const {
    children,
    className,
    closeModal,
    danger,
    inputref,
    loadingDescription,
    loadingIconDescription,
    loadingStatus,
    onLoadingSuccess,
    onRequestClose,
    onRequestSubmit,
    primaryButtonDisabled,
    primaryButtonText,
    primaryClassName,
    secondaryButtonText,
    secondaryButtons,
    secondaryClassName,
  } = props;

  warnDroppedProps('ModalFooter', {
    closeModal,
    inputref,
    loadingDescription,
    loadingIconDescription,
    loadingStatus,
    onLoadingSuccess,
    secondaryButtons,
  });

  return (
    <DialogFooter className={className}>
      {secondaryButtonText !== undefined ? (
        <Button
          variant="outline"
          className={secondaryClassName}
          onClick={onRequestClose}
        >
          {secondaryButtonText}
        </Button>
      ) : null}
      {primaryButtonText !== undefined ? (
        <Button
          variant={danger ? 'destructive' : 'default'}
          className={primaryClassName}
          disabled={primaryButtonDisabled}
          onClick={onRequestSubmit}
        >
          {primaryButtonText}
        </Button>
      ) : null}
      {children}
    </DialogFooter>
  );
}

export {ComposedModal, Modal, ModalBody, ModalFooter, ModalHeader};
