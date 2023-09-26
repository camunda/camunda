/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {useState, useEffect, useRef} from 'react';
import {Button, MenuItemSelectable, Tooltip, Menu} from '@carbon/react';
import {ChevronDown, UpdateNow} from '@carbon/icons-react';
import {useAttachedMenu} from '@camunda/camunda-optimize-composite-components';
import classnames from 'classnames';

import {t} from 'translation';

import AutoRefreshIcon from './AutoRefreshIcon';

import './AutoRefreshSelect.scss';

export default function AutoRefreshSelect({refreshRateMs, onRefresh, onChange}) {
  const [autoRefreshHandle, setAutoRefreshHandle] = useState(() =>
    refreshRateMs && onRefresh ? setInterval(onRefresh, refreshRateMs) : null
  );
  const menuRef = useRef(null);
  const triggerRef = useRef(null);
  const [width, setWidth] = useState(0);
  const {
    open,
    x,
    y,
    handleClick: hookOnClick,
    handleMousedown,
    handleClose,
  } = useAttachedMenu(triggerRef);

  function setAutorefresh(newRefreshRateMs) {
    clearInterval(autoRefreshHandle);
    if (newRefreshRateMs && onRefresh) {
      setAutoRefreshHandle(setInterval(onRefresh, newRefreshRateMs));
    }
    onChange(newRefreshRateMs);
  }

  function handleClick() {
    if (triggerRef.current) {
      const {width} = triggerRef.current.getBoundingClientRect();
      setWidth(width);
      hookOnClick();
    }
  }

  function handleOpen() {
    if (menuRef.current) {
      menuRef.current.style.width = `${width}px`;
      menuRef.current.style.maxWidth = 'unset';
    }
  }

  useEffect(() => {
    return () => {
      clearInterval(autoRefreshHandle);
    };
  }, [autoRefreshHandle]);

  const menuItems = [
    {value: 'off', label: t('common.off')},
    {value: '1', label: `1 ${t('common.unit.minute.label')}`},
    {value: '5', label: `5 ${t('common.unit.minute.label-plural')}`},
    {value: '10', label: `10 ${t('common.unit.minute.label-plural')}`},
    {value: '15', label: `15 ${t('common.unit.minute.label-plural')}`},
    {value: '30', label: `30 ${t('common.unit.minute.label-plural')}`},
    {value: '60', label: `60 ${t('common.unit.minute.label-plural')}`},
  ];

  return (
    <Tooltip label={t('dashboard.autoRefresh')} className="cds--icon-tooltip" leaveDelayMs={50}>
      <div className="AutoRefreshSelect">
        <Button
          className={classnames('cds--menu-button__trigger', {
            'cds--menu-button__trigger--open': open,
          })}
          ref={triggerRef}
          kind="ghost"
          iconDescription={t('dashboard.autoRefresh')}
          renderIcon={ChevronDown}
          aria-haspopup
          aria-expanded={open}
          onClick={handleClick}
          onMouseDown={handleMousedown}
          aria-controls={open ? 'autoRefreshSelect' : undefined}
        >
          {onRefresh ? <AutoRefreshIcon interval={refreshRateMs} /> : <UpdateNow />}
        </Button>
        <Menu
          ref={menuRef}
          target={triggerRef.current}
          id={'autoRefreshSelect'}
          label={t('dashboard.autoRefresh')}
          open={open}
          onClose={handleClose}
          onOpen={handleOpen}
          x={x}
          y={y}
        >
          {menuItems.map(({value, label}) => {
            const currentValue = !refreshRateMs ? 'off' : (refreshRateMs / (60 * 1000)).toString();
            return (
              <MenuItemSelectable
                key={value}
                value={value}
                label={label}
                selected={value === currentValue}
                onChange={() => setAutorefresh(value === 'off' ? null : value * 60 * 1000)}
              />
            );
          })}
        </Menu>
      </div>
    </Tooltip>
  );
}
