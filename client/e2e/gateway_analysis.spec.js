const {expect} = require('chai');

describe('Gateway Analysis', () => {
  afterEach(() => {
    browser.localStorage('DELETE');
  });

  it('should open the analysis diagram section', () => {
    // LOGIN
    browser.url('/');
    browser.waitForEnabled('input[type="text"]');

    browser.setValue('input[type="text"]', 'admin');
    browser.setValue('input[type="password"]', 'admin');
    browser.click('button[type="submit"]');

    browser.waitForExist('.processDefinitionSelect option[value]:not([value=""])');

    // SELECT VIEW
    browser.selectByVisibleText('.view-select', 'Frequency');

    // SELECT PROCESS DEFINITION
    browser.selectByVisibleText('.processDefinitionSelect', 'Process_1');
    const endEventSelector = '.djs-element.djs-shape.highlight';

    browser.waitForExist(endEventSelector);

    // EXPECT A HEATMAP
    expect(browser.isVisible('.viewport image')).to.eql(true);

    // OPEN THE MODAL
    browser.click(endEventSelector);
    browser.waitForVisible('.startGatewayAnalysis');

    // EXPECT STATISTICS
    expect(browser.isVisible('.end-event-statistics')).to.eql(true);

    // CLICK THE START GATEWAY ANALYSIS BUTTON
    browser.click('.startGatewayAnalysis');

    // CLICK THE gateway
    browser.waitForVisible('.startGatewayAnalysis', 2000, true);
    const gatewaySelector = '.djs-element.djs-shape.highlight';

    browser.waitForVisible(gatewaySelector);
    browser.click(gatewaySelector);

    // EXPECT STATISTICS
    browser.waitForVisible('.statisticsContainer');
    expect(browser.isVisible('.statisticsContainer')).to.eql(true);
  });
});
