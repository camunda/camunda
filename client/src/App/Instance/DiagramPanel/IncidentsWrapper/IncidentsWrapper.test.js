import React from 'react';
import {mount} from 'enzyme';

import {ThemeProvider} from 'modules/contexts/ThemeContext';
import {createIncidents} from 'modules/testUtils';

import IncidentsWrapper from './IncidentsWrapper';
import IncidentsOverlay from '../IncidentsOverlay';
import IncidentsBar from '../IncidentsBar';

const mockProps = {
  incidents: createIncidents(),
  instanceId: '3'
};

describe('IncidentsWrapper', () => {
  it('should hide the IncidentsOverlay by default', () => {
    const node = mount(
      <ThemeProvider>
        <IncidentsWrapper {...mockProps} />
      </ThemeProvider>
    );
    expect(node.find(IncidentsOverlay)).not.toExist();
  });

  it('should toggle the IncidentsOverlay when clicking on the IncidentsBar', () => {
    const node = mount(
      <ThemeProvider>
        <IncidentsWrapper {...mockProps} />
      </ThemeProvider>
    );

    // open overlay
    node.find(IncidentsBar).simulate('click');
    node.update();
    expect(node.find(IncidentsOverlay).length).toEqual(1);

    // close overlay
    node.find(IncidentsBar).simulate('click');
    node.update();
    expect(node.find(IncidentsOverlay).length).toEqual(0);
  });
});
