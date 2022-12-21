// vim: set filetype=groovy:

organizationFolder('camunda') {

    description('Jobs for https://github.com/camunda')
    displayName('camunda')

    organizations {
        github {
            repoOwner('camunda')
            credentialsId('github-camunda-zeebe-app')

            traits {
                cleanBeforeCheckoutTrait {
                    extension {
                        deleteUntrackedNestedRepositories(false)
                    }
                }
                // Discover branches.
                // Strategy ID 3 => Discover all branches.
                gitHubBranchDiscovery {
                    strategyId 3
                }
                pruneStaleBranchTrait()
                localBranchTrait()
                sourceWildcardFilter {
                  includes('zeebe*')
                  excludes('')
                }

                // Disable sending Github status notifications as GHA CI is default
                githubSkipNotifications()
            }
        }
    }

    orphanedItemStrategy {
        discardOldItems {
            numToKeep 10
        }
    }

    triggers {
        periodicFolderTrigger {
            interval '8h'
        }
    }

    properties {
        // Avoid automatically build jobs on non-prod envs by org indexing.
        // Note: The DSL name here is misleading. This config is for the branches that WILL be built automatically.
        // In the course of the GHA migration automatic builds on Jenkins were disabled
        noTriggerOrganizationFolderProperty {
            branches ('')
        }
    }

}
