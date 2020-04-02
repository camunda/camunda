import React, {useState, useRef} from 'react';
import classnames from 'classnames';

import {Button, Icon, Input} from 'components';
import {t} from 'translation';

import './SearchField.scss';

export default function SearchField({value, onChange = () => {}}) {
  const [open, setOpen] = useState(false);
  const input = useRef();

  return (
    <div className="SearchField">
      <Button
        icon
        onClick={() => {
          setOpen(!open);
          onChange('');

          if (!open && input.current) {
            input.current.focus();
          }
        }}
      >
        <Icon type="search" className={classnames({hidden: open})} />
        <Icon type="search-reset" className={classnames({hidden: !open})} />
      </Button>
      <Input
        className={classnames({open})}
        value={value}
        placeholder={t('home.search.name')}
        onChange={evt => onChange(evt.target.value)}
        onKeyDown={({key}) => {
          if (key === 'Escape') {
            setOpen(false);
            onChange('');
          }
        }}
        ref={input}
      />
    </div>
  );
}
