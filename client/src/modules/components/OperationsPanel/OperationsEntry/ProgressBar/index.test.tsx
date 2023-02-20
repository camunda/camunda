/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {render, screen} from 'modules/testing-library';
import {ProgressBar} from './index';
import {ThemeProvider} from 'modules/theme/ThemeProvider';

describe('ProgressBar', () => {
  it('should render less than 0% progress', async () => {
    render(<ProgressBar progressPercentage={-100} />, {
      wrapper: ThemeProvider,
    });

    expect(screen.getByTestId('progress-bar')).toHaveStyleRule('width', '0%');
  });

  it('should render 0% progress', async () => {
    render(<ProgressBar progressPercentage={0} />, {
      wrapper: ThemeProvider,
    });
    expect(screen.getByTestId('progress-bar')).toHaveStyleRule('width', '0%');
  });

  it('should render 33% progress', async () => {
    render(<ProgressBar progressPercentage={33} />, {
      wrapper: ThemeProvider,
    });
    expect(screen.getByTestId('progress-bar')).toHaveStyleRule('width', '33%');
  });

  it('should render 100% progress', async () => {
    render(<ProgressBar progressPercentage={100} />, {
      wrapper: ThemeProvider,
    });
    expect(screen.getByTestId('progress-bar')).toHaveStyleRule('width', '100%');
  });

  it('should not render more than 100% progress', async () => {
    render(<ProgressBar progressPercentage={200} />, {
      wrapper: ThemeProvider,
    });
    expect(screen.getByTestId('progress-bar')).toHaveStyleRule('width', '100%');
  });
});
