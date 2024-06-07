/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React, {
  useEffect,
  useState,
  useRef,
  ComponentProps,
  ComponentPropsWithoutRef,
  ReactElement,
  UIEvent,
} from 'react';
import classnames from 'classnames';

import {Select} from 'components';
import {ignoreFragments} from 'services';

import DropdownOption from './DropdownOption';
import Submenu from './Submenu';

import './DropdownOptionsList.scss';

interface DropdownOptionsListProps extends ComponentPropsWithoutRef<'ul'> {
  open?: boolean;
  closeParent?: () => void;
}

export default function DropdownOptionsList({
  open,
  closeParent,
  children,
  className,
  ...props
}: DropdownOptionsListProps) {
  const [openSubmenu, setOpenSubmenu] = useState<number | null>(null);
  const [fixedSubmenu, setFixedSubmenu] = useState<number | null>(null);

  const scheduledRemove = useRef<ReturnType<typeof setTimeout>>();
  const optionsListRef = useRef<HTMLUListElement>(null);

  useEffect(() => {
    setOpenSubmenu(null);
    setFixedSubmenu(null);
  }, [open]);

  const closeSubmenu = () => setOpenSubmenu(null);

  const renderChild = (child: ReactElement, idx: number) => {
    if (child?.type === Submenu || child?.type === Select.Submenu) {
      return React.cloneElement<ComponentProps<typeof Submenu>>(child, {
        open: fixedSubmenu === idx || (fixedSubmenu === null && openSubmenu === idx),
        fixed: fixedSubmenu === idx,
        offset: optionsListRef.current?.offsetWidth,
        setOpened: () => {
          clearTimeout(scheduledRemove.current);
          setOpenSubmenu(idx);
        },
        setClosed: () => {
          scheduledRemove.current = setTimeout(closeSubmenu, 300);
        },
        onMenuMouseEnter: () => {
          clearTimeout(scheduledRemove.current);
        },
        onMenuMouseLeave: closeSubmenu,
        forceToggle: (evt) => {
          evt.stopPropagation();
          evt.preventDefault();
          setFixedSubmenu(fixedSubmenu === idx ? null : idx);
        },
        closeParent,
      });
    } else if (child?.type === DropdownOption || child?.type === Select.Option) {
      return React.cloneElement<ComponentProps<typeof DropdownOption>>(child, {
        onMouseEnter: (evt: UIEvent<HTMLElement>) => {
          child.props.onMouseEnter?.(evt);
          if (!(evt.target as HTMLElement | null)?.classList.contains('disabled')) {
            closeSubmenu();
          }
        },
      });
    } else {
      return child;
    }
  };

  return (
    <ul className={classnames('DropdownOptionsList', className)} ref={optionsListRef} {...props}>
      {React.Children.map(ignoreFragments(children), (child, idx) => (
        <li key={idx}>{renderChild(child, idx)}</li>
      ))}
    </ul>
  );
}
