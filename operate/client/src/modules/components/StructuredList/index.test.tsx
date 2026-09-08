/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {render, screen} from 'modules/testing-library';
import {StructuredList} from '.';

describe('<StructuredList />', () => {
  const headerColumns = [
    {cellContent: 'Property', width: '30%'},
    {cellContent: 'Value', width: '70%'},
  ];
  const rows = [
    {
      key: 'row-1',
      columns: [{cellContent: 'Flow Node Instance Key'}, {cellContent: '123'}],
    },
  ];

  it('should not apply left padding to any cell when valueCellPadding is not set', () => {
    render(
      <StructuredList label="Details" headerColumns={headerColumns} rows={rows} />,
    );

    expect(screen.getByText('Flow Node Instance Key')).not.toHaveStyle({
      paddingLeft: '8px',
    });
    expect(screen.getByText('123')).not.toHaveStyle({paddingLeft: '8px'});
  });

  it('should apply left padding only to the value column when valueCellPadding is set', () => {
    render(
      <StructuredList
        label="Details"
        headerColumns={headerColumns}
        rows={rows}
        valueCellPadding="8px"
      />,
    );

    expect(screen.getByText('Flow Node Instance Key')).not.toHaveStyle({
      paddingLeft: '8px',
    });
    expect(screen.getByText('123')).toHaveStyle({paddingLeft: '8px'});
  });
});
