import {IdentityPage} from '../SM-8.5/IdentityPage';
import {NavigationPage} from '../SM-8.5/NavigationPage';

export async function deleteAllUserGroups(
  navigationPage: NavigationPage,
  identityPage: IdentityPage,
): Promise<void> {
  await navigationPage.goToIdentity();
  await identityPage.clickGroupsTab();
  await identityPage.deleteAllGroups();
}
