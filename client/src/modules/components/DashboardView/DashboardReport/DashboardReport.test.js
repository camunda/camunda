import React from 'react';
import {mount} from 'enzyme';

import DashboardReport from './DashboardReport';

jest.mock('./ExternalReport', () => ({children}) => <span>ExternalReport: {children()}</span>);
jest.mock('./OptimizeReport', () => ({children}) => <span>OptimizeReport: {children()}</span>);

// jest.mock('react-router-dom', () => {
//   return {
//     Redirect: ({to}) => {
//       return <div>REDIRECT to {to}</div>;
//     },
//     Link: ({children, to, onClick, id}) => {
//       return (
//         <a id={id} href={to} onClick={onClick}>
//           {children}
//         </a>
//       );
//     }
//   };
// });

// jest.mock('components', () => {
//   return {
//     ReportView: () => <div>ReportView</div>
//   };
// });

// const loadReport = jest.fn();

const props = {
  report: {
    id: 'a'
  }
  // loadReport
};

// it('should load the report provided by id', () => {
//   mount(<DashboardReport {...props} />);

//   expect(loadReport).toHaveBeenCalledWith({...props.report});
// });

// it('should render the ReportView if data is loaded', async () => {
//   loadReport.mockReturnValue('data');

//   const node = mount(<DashboardReport {...props} />);

//   await node.instance().loadReportData();

//   expect(node).toIncludeText('ReportView');
// });

// it('should render an error message if report rendering went wrong', async () => {
//   loadReport.mockReturnValue({errorMessage: 'I AM BROKEN!'});

//   const node = mount(<DashboardReport {...props} />);

//   await node.instance().loadReportData();

//   expect(node).toIncludeText('I AM BROKEN!');
// });

// it('should contain the report name', async () => {
//   loadReport.mockReturnValue({name: 'Report Name'});
//   const node = mount(<DashboardReport {...props} />);

//   await node.instance().loadReportData();

//   expect(node).toIncludeText('Report Name');
// });

it('should render optional addons', () => {
  // loadReport.mockReturnValue('data');

  const TextRenderer = ({children}) => <p>{children}</p>;

  const node = mount(
    <DashboardReport
      {...props}
      addons={[<TextRenderer key="textAddon">I am an addon!</TextRenderer>]}
    />
  );

  expect(node).toIncludeText('I am an addon!');
});

it('should pass properties to report addons', () => {
  const PropsRenderer = props => <p>{JSON.stringify(Object.keys(props))}</p>;

  const node = mount(
    <DashboardReport {...props} addons={[<PropsRenderer key="propsRenderer" />]} />
  );

  expect(node).toIncludeText('report');
  expect(node).toIncludeText('tileDimensions');
});

// it('should provide a link to the report', async () => {
//   loadReport.mockReturnValue({name: 'Report Name'});
//   const node = mount(<DashboardReport {...props} />);

//   await node.instance().loadReportData();
//   node.update();

//   expect(node.find('a')).toIncludeText('Report Name');
//   expect(node.find('a')).toHaveProp('href', '/report/a');
// });

// it('should not provide a link to the report when link is disabled', async () => {
//   loadReport.mockReturnValue({name: 'Report Name'});
//   const node = mount(<DashboardReport {...props} disableNameLink />);

//   await node.instance().loadReportData();
//   node.update();

//   expect(node.find('a')).not.toBePresent();
//   expect(node).toIncludeText('Report Name');
// });

// it('should display the name of a failing report', async () => {
//   loadReport.mockReturnValue({
//     errorMessage: 'Is failing',
//     reportDefinition: {name: 'Failing Name'}
//   });
//   const node = mount(<DashboardReport {...props} disableNameLink />);

//   await node.instance().loadReportData();

//   expect(node).toIncludeText('Failing Name');
// });

// describe('external report', () => {
//   const loadReport = jest.fn();

//   const props = {
//     report: {
//       id: '',
//       configuration: {
//         external: 'externalURL'
//       }
//     },
//     loadReport
//   };

//   it('should not load report data for an external report', () => {
//     mount(<DashboardReport {...props} />);

//     expect(loadReport).not.toHaveBeenCalled();
//   });

//   it('should include an iframe with the provided external url', () => {
//     const node = mount(<DashboardReport {...props} />);

//     const iframe = node.find('iframe');

//     expect(iframe).toBePresent();
//     expect(iframe).toHaveProp('src', 'externalURL');
//   });
// });
