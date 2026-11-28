import {expect} from '@playwright/test';
import {test} from 'fixtures';
import {createSecretAuthorization} from 'utils/constants';
import {captureScreenshot, captureFailureVideo} from '@setup';

/**
 * External configuration via cluster variables (epic camunda/product-hub#2717).
 *
 * TODO: this spec file collects the UI cases for centrally managed cluster
 * variables / secrets. They share a file because they all exercise the same
 * surface (Identity's central configuration screens) and the same lifecycle
 * (a randomised owner + a randomised cluster variable entry that must be torn
 * down together); splitting them would duplicate that setup/teardown.
 *
 * TODO: no user story exists for ECV-01 — it is written by hand from the
 * acceptance criterion "use a standardised, secure mechanism to manage secrets
 * and credentials, achieving parity between SaaS and Self-managed
 * environments".
 */
test.describe('External configuration via cluster variables', () => {
  const uniqueId = `ecv01${Date.now()}${Math.floor(Math.random() * 10000)}`;

  const NEW_USER = {
    username: `user${uniqueId}`,
    name: `User ${uniqueId}`,
    email: `user${uniqueId}@test.com`,
    password: 'Password1!',
  };

  let authorizationCreated = false;
  let userCreated = false;

  test.afterEach(
    async ({page, identityAuthorizationsPage, identityUsersPage}, testInfo) => {
      await captureScreenshot(page, testInfo);
      await captureFailureVideo(page, testInfo);

      if (authorizationCreated) {
        try {
          await identityAuthorizationsPage.navigateToAuthorizations();
          await identityAuthorizationsPage.clickResourceType('Secret');
          await identityAuthorizationsPage.clickDeleteAuthorizationButton(
            NEW_USER.username,
          );
          await identityAuthorizationsPage.clickDeleteAuthorizationSubButton();
        } catch {
          // authorization already gone or never persisted - nothing to clean up
        } finally {
          authorizationCreated = false;
        }
      }

      if (userCreated) {
        try {
          await identityUsersPage.navigateToUsers();
          await identityUsersPage.deleteUser(NEW_USER);
        } catch {
          // user already gone or never persisted - nothing to clean up
        } finally {
          userCreated = false;
        }
      }
    },
  );

  test('ECV-01: a cluster variable (secret) is managed centrally and granted to a user', async ({
    identityUsersPage,
    identityAuthorizationsPage,
  }) => {
    // given
    await test.step('an existing user who needs access to central configuration', async () => {
      await identityUsersPage.navigateToUsers();
      await identityUsersPage.createUser(NEW_USER);
      userCreated = true;
    });

    // when
    await test.step('a secret (cluster variable) authorization is granted to that user', async () => {
      await identityAuthorizationsPage.navigateToAuthorizations();
      await identityAuthorizationsPage.clickResourceType('Secret');
      await identityAuthorizationsPage.createAuthorization(
        createSecretAuthorization(NEW_USER.username),
      );
      authorizationCreated = true;
    });

    // then
    await test.step('the secret access is centrally listed for that user', async () => {
      await identityAuthorizationsPage.navigateToAuthorizations();
      await identityAuthorizationsPage.clickResourceType('Secret');

      const authorizationRow =
        await identityAuthorizationsPage.findAuthorizationInPaginatedList(
          NEW_USER.username,
          'Secret',
        );
      await expect(authorizationRow).toBeVisible();

      await identityAuthorizationsPage.assertAuthorizationExists(
        NEW_USER.username,
        'User',
      );
    });
  });
});
