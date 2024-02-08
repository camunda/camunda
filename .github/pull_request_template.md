Linked issue: 

## PR Checklist

- [ ] This PR is targeting the correct branch
<!-- If it's a fix during a regular release, this PR should target only the release branch -->
<!-- If it's a fix during a patch release, This PR should target the maintenance branch AND you have to make sure it is manually cherry-picked to the master branch as well, since the maintenance branch DOES NOT get merged back to master. -->
<!-- Otherwise, this PR should target the master branch -->
- [ ] If required, I have added testing notes to the Github issue
- [ ] If there are changes to a translation file, I have added the testing of relevant translations to the testing notes
- [ ] If there are new Mixpanel events, I have informed Eran 
- [ ] If there are API changes, I have updated our [Rest API docs](https://confluence.camunda.com/display/CO/REST-API)
- [ ] If there are external API changes, I have messaged [Christina](@christinaausley) to let her know of the changes
- [ ] If there are schema changes, this PR includes a migration script or a link to a follow-up task for the migration script
- [ ] If this change needs backporting to any older maintenance branches, I have added the respective backporting label to the PR
