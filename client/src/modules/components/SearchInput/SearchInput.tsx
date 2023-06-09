/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {Input, Icon, InputProps} from 'components';

import './SearchInput.scss';

export default function SearchInput({...props}: InputProps) {
  return (
    <div className="SearchInput">
      <Input className="searchInput" type="text" {...props} />
      <Icon className="searchIcon" type="search" size="20" />
    </div>
  );
}
