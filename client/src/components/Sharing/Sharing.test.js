import React from 'react';
import { mount } from 'enzyme';

import Sharing from './Sharing';
import {getReportData} from './service';

jest.mock('./service', () => {
return {
    getReportData: jest.fn()
}
});

jest.mock('components', () =>{
return {
    ReportView: () => <div id='report' >ReportView</div>
    }
});

const props = {
    match : {
        params: {
            id: 123
        }
    }
}

it('should render without crashing', () => {
    mount(<Sharing {...props}/>);
  });

it('should initially load data', () => {
    mount(<Sharing {...props} />);

    expect(getReportData).toHaveBeenCalled();
});

it('should display a loading indicator', () => {
    const node = mount(<Sharing {...props} />);

    expect(node.find('.Sharing__loading-indicator')).toBePresent();
});

it('should display a loading indicator', () => {
    const node = mount(<Sharing {...props} />);

    node.setState({
        loaded: true,
        reportResult: null
    });

    expect(node.find('.Sharing__error-message')).toBePresent();
});

it('should have report if everything is fine', () => {
    const node = mount(<Sharing {...props} />);
    
    node.setState({
        loaded: true,
        reportResult: { report: {name: 'foo'}}
    });

    expect(node.find('#report')).toIncludeText('ReportView');
});

it('should retrieve report for the given id', () => {
    const node = mount(<Sharing {...props} />);

    expect(getReportData).toHaveBeenCalledWith(123);
});