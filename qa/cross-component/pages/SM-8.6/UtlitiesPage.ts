import {IdentityPage} from '../SM-8.6/IdentityPage';
import {NavigationPage} from '../SM-8.6/NavigationPage';

export async function deleteAllUserGroups(
  navigationPage: NavigationPage,
  identityPage: IdentityPage,
): Promise<void> {
  await navigationPage.goToIdentity();
  await identityPage.clickGroupsTab();
  await identityPage.deleteAllGroups();
}
