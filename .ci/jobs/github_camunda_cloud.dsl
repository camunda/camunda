// vim: set filetype=groovy:

organizationFolder('camunda-cloud') {

    description('Jobs for https://github.com/camunda-cloud')
    displayName('camunda-cloud')

    organizations {
        github {
            repoOwner('camunda-cloud')
            credentialsId('github-cloud-zeebe-app')

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
                  includes('zeebe*')
                  excludes('')
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
        // Note: the 'traits' variable can be used to configure options that are
        // not exposed via normal Jenkins API like above 'gitHubBranchDiscovery'
    }
}
