/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useNavigate} from 'react-router-dom';
import styles from './TabListNav.module.scss';
import cn from 'classnames';

type Props = {
  className?: string;
  label: string;
  items: Array<{
    key: string;
    title: string;
    label: string;
    selected: boolean;
    href: string;
    visible?: boolean;
  }>;
};

const TabListNav: React.FC<Props> = ({className, label, items}) => {
  const navigate = useNavigate();
  return (
    <nav className={cn(className, styles.tabs, 'cds--tabs')}>
      <div className="cds--tab--list" aria-label={label}>
        {items.map(({key, title, label, selected, href, visible}) => {
          const isHidden = visible === false;
          return (
            <button
              key={key}
              type="button"
              role="link"
              aria-label={label}
              aria-current={selected ? 'page' : undefined}
              className={cn(
                {[styles.hidden]: isHidden},
                'cds--tabs__nav-item',
                'cds--tabs__nav-link',
                {
                  ['cds--tabs__nav-item--selected']: selected,
                },
              )}
              tabIndex={selected ? 0 : -1}
              hidden={isHidden}
              aria-hidden={isHidden}
              onClick={() => navigate(href)}
            >
              <div className="cds--tabs__nav-item-label-wrapper">
                <span className="cds--tabs__nav-item-label">{title}</span>
              </div>
            </button>
          );
        })}
      </div>
    </nav>
  );
};

export {TabListNav};
