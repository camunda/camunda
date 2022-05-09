/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React, {useEffect, useState, useRef} from 'react';
import classnames from 'classnames';

import {Select} from 'components';

import DropdownOption from './DropdownOption';
import Submenu from './Submenu';

import './DropdownOptionsList.scss';

export default function DropdownOptionsList({open, closeParent, children, className, ...props}) {
  const [openSubmenu, setOpenSubmenu] = useState(null);
  const [fixedSubmenu, setFixedSubmenu] = useState(null);

  const scheduledRemove = useRef();
  const optionsListRef = useRef();

  useEffect(() => {
    setOpenSubmenu(null);
    setFixedSubmenu(null);
  }, [open]);

  const closeSubmenu = () => setOpenSubmenu(null);

  const renderChild = (child, idx) => {
    if (child?.type === Submenu || child?.type === Select.Submenu) {
      return React.cloneElement(child, {
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
      return React.cloneElement(child, {
        onMouseEnter: (evt) => {
          child.props.onMouseEnter?.(evt);
          if (!evt.target.classList.contains('disabled')) {
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
      {React.Children.map(children, (child, idx) => (
        <li key={idx}>{renderChild(child, idx)}</li>
      ))}
    </ul>
  );
}
