/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useLocation, useNavigate, type Path} from 'react-router-dom';
import styles from './styles.module.scss';
import cn from 'classnames';

type Props = {
  className?: string;
  label: string;
  items: Array<{
    key: string;
    title: string;
    label: string;
    selected: boolean;
    to: Partial<Path>;
    visible?: boolean;
  }>;
};

const TabListNav: React.FC<Props> = ({className, label, items}) => {
  const location = useLocation();
  const navigate = useNavigate();

  return (
    <nav className={cn(className, styles.tabs, 'cds--tabs')}>
      <div className="cds--tab--list" aria-label={label}>
        {items.map(({key, title, label, selected, to, visible}) => {
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
              hidden={isHidden}
              aria-hidden={isHidden}
              onClick={() =>
                navigate({
                  ...location,
                  ...to,
                })
              }
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
