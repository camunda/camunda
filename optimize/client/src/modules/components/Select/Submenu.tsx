/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {MenuItemSelectable} from '@carbon/react';
import {ComponentProps, ReactNode, useLayoutEffect, useRef} from 'react';

type SubmenuProps = Omit<ComponentProps<typeof MenuItemSelectable>, 'label'> & {
  label?: string | JSX.Element[];
  children?: ReactNode;
  disabled?: boolean;
};

export default function Submenu(props: SubmenuProps) {
  const submenuRef = useRef<HTMLLIElement>(null);

  useLayoutEffect(() => {
    const submenu = submenuRef.current?.querySelector('.cds--menu') as HTMLLIElement;
    const trigger = submenuRef.current as HTMLElement;

    if (!submenu || !trigger) {
      return;
    }

    const applySubmenuStyles = () => {
      // reset library positioning
      submenu.style.transform = 'none';
      submenu.style.left = 'auto';
      submenu.style.top = 'auto';

      const triggerRect = trigger.getBoundingClientRect();
      const submenuRect = submenu.getBoundingClientRect();

      let left = triggerRect.right;
      if (triggerRect.right + submenuRect.width > window.innerWidth) {
        left = triggerRect.left - submenuRect.width;
      }

      // Adjust vertically if submenu would overflow the viewport
      const overflowAmount = triggerRect.top + submenuRect.height - window.innerHeight;
      const transformY = overflowAmount > 0 ? -overflowAmount - 8 : 0; // 8px padding

      // Apply custom positioning
      Object.assign(submenu.style, {
        top: `${triggerRect.top}px`,
        left: `${left}px`,
        transform: `translateY(${transformY}px)`,
      });
    };

    const observer = new MutationObserver(() => {
      observer.disconnect(); // prevent loops
      applySubmenuStyles();
      observer.observe(submenu, {attributes: true, attributeFilter: ['style']});
    });

    observer.observe(submenu, {attributes: true, attributeFilter: ['style']});
    return () => observer.disconnect();
  }, []);

  return (
    // To make disabled state work, we can't pass children to it
    <MenuItemSelectable
      ref={submenuRef}
      className="Submenu"
      {...props}
      label={props.label?.toString() || ''}
    >
      {!props.disabled && props.children}
    </MenuItemSelectable>
  );
}
