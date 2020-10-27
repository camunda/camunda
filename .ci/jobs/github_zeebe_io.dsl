// vim: set filetype=groovy:

organizationFolder('zeebe-io') {

    description('Jobs for https://github.com/zeebe-io')
    displayName('zeebe-io')

    organizations {
        github {
            repoOwner('zeebe-io')
            credentialsId('github-zeebe-app')

            traits {
                cleanBeforeCheckoutTrait {
                    extension {
                        deleteUntrackedNestedRepositories(false)
                    }
                }
                gitHubBranchDiscovery {
                    strategyId(3)
                }
                pruneStaleBranchTrait()
                localBranchTrait()
                sourceWildcardFilter {
                  includes('*')
                  excludes('zeebe-tasklist')
                }
            }
        }
    }

    orphanedItemStrategy {
        discardOldItems {
            numToKeep(10)
        }
    }

    triggers {
        periodicFolderTrigger {
            interval('8h')
        }
    }

    configure {

        def traits = it / navigators / 'org.jenkinsci.plugins.github__branch__source.GitHubSCMNavigator' / traits
        // Note: disable discovery of Pull Requests as of https://jira.camunda.com/browse/INFRA-1924
        // traits << 'org.jenkinsci.plugins.github__branch__source.OriginPullRequestDiscoveryTrait' {
        //     strategyId 2
        // }
    }

}

