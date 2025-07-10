/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {test} from 'fixtures';
import {navigateToApp} from '@pages/UtilitiesPage';
import {expect} from '@playwright/test';
import {captureScreenshot, captureFailureVideo} from '@setup';
import {relativizePath, Paths} from 'utils/relativizePath';

test.describe.parallel('authorizations page', () => {
    test.beforeEach(async ({page}) => {
      await navigateToApp(page, 'identity');
    });
  
    test.afterEach(async ({page}, testInfo) => {
      await captureScreenshot(page, testInfo);
      await captureFailureVideo(page, testInfo);
    });



    test('CRUD authorization for user', async ({loginPage, page}) => {
        await test.step('Add authorization', async () => {
         

        })
        
        await test.step('Remove authorization', async () => {


        })

        await expect(loginPage.passwordInput).toHaveAttribute('type', 'password');
    
        await loginPage.login('demo', 'wrong-password');
        await expect(page).toHaveURL(`${relativizePath(Paths.login('identity'))}`);
    
        await expect(loginPage.errorMessage).toContainText(
          "Username and password don't match",
        );
    
        await expect(page).toHaveURL(`${relativizePath(Paths.login('identity'))}`);
      });

    })