package main

# This file contains rules checking GHA workflow files of the Unified CI to be
# aligned with the inclusion criteria and best practices:
# See https://github.com/camunda/camunda/wiki/CI-&-Automation#unified-ci

# The `input` variable is a data structure that contains a YAML file's contents
# as objects and arrays. See https://www.openpolicyagent.org/docs/latest/philosophy/#how-does-opa-work

deny[msg] {
    # only enforced on workflows that opted-in
    input.env.GHA_BEST_PRACTICES_LINTER == "enabled"

    count(get_jobs_without_timeoutminutes(input.jobs)) > 0

    msg := sprintf("There are GitHub Actions jobs without timeout-minutes! Affected job IDs: %s",
        [concat(", ", get_jobs_without_timeoutminutes(input.jobs))])
}

warn[msg] {
    # only enforced on workflows that opted-in
    input.env.GHA_BEST_PRACTICES_LINTER == "enabled"

    count(get_jobs_with_timeoutminutes_higher_than(input.jobs, 15)) > 0

    msg := sprintf("There are GitHub Actions jobs with too high (>15) timeout-minutes! Affected job IDs: %s",
        [concat(", ", get_jobs_with_timeoutminutes_higher_than(input.jobs, 15))])
}

deny[msg] {
    # only enforced on workflows that opted-in
    input.env.GHA_BEST_PRACTICES_LINTER == "enabled"

    count(get_jobs_without_cihealth(input.jobs)) > 0

    msg := sprintf("There are GitHub Actions jobs that don't send CI Health metrics! Affected job IDs: %s",
        [concat(", ", get_jobs_without_cihealth(input.jobs))])
}

deny[msg] {
    # only enforced on workflows that opted-in
    input.env.GHA_BEST_PRACTICES_LINTER == "enabled"

    count(get_jobs_without_permissions(input.jobs)) > 0

    msg := sprintf("There are GitHub Actions jobs using default GITHUB_TOKEN permissions which are too wide! Affected job IDs: %s",
        [concat(", ", get_jobs_without_permissions(input.jobs))])
}

deny[msg] {
    # only enforced on Unified CI since it is specific to detect-changes job
    input.name == "CI"

    count(get_jobs_not_needing_detectchanges(input.jobs)) > 0

    msg := sprintf("There are GitHub Actions jobs in Unified CI that don't depend on detect-changes job! Affected job IDs: %s",
        [concat(", ", get_jobs_not_needing_detectchanges(input.jobs))])
}

deny[msg] {
    # only enforced on Unified CI since it is specific to check-results job
    input.name == "CI"

    jobs_that_should_fail_checkresults := { job_id |
        job := input.jobs[job_id]

        # no Unified CI jobs running after (and including) "check-results" job
        job_id != "check-results"
        not startswith(job_id, "deploy-")
    }

    jobs_that_actually_fail_checkresults := {x | x := input.jobs["check-results"].needs[_]}

    jobs_that_should_fail_checkresults != jobs_that_actually_fail_checkresults

    msg := sprintf("There are GitHub Actions jobs in Unified CI that check-results job doesn't depend on! Affected job IDs: %s",
        [concat(", ", jobs_that_should_fail_checkresults - jobs_that_actually_fail_checkresults)])
}

###########################   RULE HELPERS   ##################################

get_jobs_without_timeoutminutes(jobInput) = jobs_without_timeoutminutes {
    jobs_without_timeoutminutes := { job_id |
        job := jobInput[job_id]

        # not enforced on jobs that invoke other reusable workflows (instead enforced there)
        not job.uses

        # check if there is timeout-minutes specified
        not job["timeout-minutes"]
    }
}

get_jobs_with_timeoutminutes_higher_than(jobInput, max_timeout) = jobs_without_timeoutminutes {
    jobs_without_timeoutminutes := { job_id |
        job := jobInput[job_id]

        # not enforced on jobs that invoke other reusable workflows (instead enforced there)
        not job.uses

        # check if timeout-minutes is higher than allowed maximum
        job["timeout-minutes"] > max_timeout
    }
}

get_jobs_without_cihealth(jobInput) = jobs_without_cihealth {
    jobs_without_cihealth := { job_id |
        job := jobInput[job_id]

        # not enforced on "special" jobs needed for control flow in Unified CI
        job_id != "detect-changes"
        job_id != "check-results"
        job_id != "test-summary"

        # not enforced on jobs that invoke other reusable workflows (instead enforced there)
        not job.uses

        # check that there is at least one step that invokes CI Health metric submission helper action
        cihealth_steps := { step |
            step := job.steps[_]
            step.name == "Observe build status"
            step.uses == "./.github/actions/observe-build-status"
        }
        count(cihealth_steps) < 1
    }
}

get_jobs_without_permissions(jobInput) = jobs_without_permissions {
    jobs_without_permissions := { job_id |
        job := jobInput[job_id]

        # not enforced on jobs that invoke other reusable workflows (instead enforced there)
        not job.uses

        not job.permissions
    }
}

get_jobs_not_needing_detectchanges(jobInput) = jobs_not_needing_detectchanges {
    jobs_not_needing_detectchanges := { job_id |
        job := jobInput[job_id]

        # not enforced on Unified CI jobs that are part of change detection control flow structure
        job_id != "detect-changes"
        job_id != "check-results"

        # not enforced on Unified CI jobs running after "check-results" job
        not startswith(job_id, "deploy-")

        # check if job declares dependency on "detect-changes" job anywhere
        job_needs_detectchanges := { need |
            need := job.needs[_]
            need == "detect-changes"
        }
        count(job_needs_detectchanges) == 0
    }
}
