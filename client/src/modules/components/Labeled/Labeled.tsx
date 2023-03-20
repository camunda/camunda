/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {ComponentPropsWithoutRef, MouseEvent} from 'react';

import classnames from 'classnames';
import './Labeled.scss';

interface LabeledProps extends ComponentPropsWithoutRef<'label'> {
  label: string | JSX.Element[];
  appendLabel?: boolean;
  disabled?: boolean;
}

export default function Labeled({
  label,
  className,
  appendLabel,
  children,
  disabled,
  ...props
}: LabeledProps) {
  return (
    <div className={classnames('Labeled', className, {disabled})}>
      <label className={classnames({checkLabel: appendLabel})} onClick={catchClick} {...props}>
        {!appendLabel && <span className="label before">{label}</span>}
        {children}
        {appendLabel && <span className="label after">{label}</span>}
      </label>
    </div>
  );
}

function catchClick(evt: MouseEvent<HTMLElement>) {
  const eventTarget = evt.target as HTMLElement;
  if (
    !eventTarget.classList.contains('label') &&
    !eventTarget.closest('.label') &&
    !eventTarget.classList.contains('Input')
  ) {
    evt.preventDefault();
  }
}
