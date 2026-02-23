package main

# This file contains rules checking GHA workflow files of the Unified CI to be
# aligned with the inclusion criteria and best practices:
# See https://camunda.github.io/camunda/ci/#unified-ci

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
        not startswith(job_id, "utils-")
    }

    jobs_that_actually_fail_checkresults := {x | x := input.jobs["check-results"].needs[_]}

    jobs_that_should_fail_checkresults != jobs_that_actually_fail_checkresults

    msg := sprintf("There are GitHub Actions jobs in Unified CI that check-results job doesn't depend on! Affected job IDs: %s",
        [concat(", ", jobs_that_should_fail_checkresults - jobs_that_actually_fail_checkresults)])
}

deny[msg] {
    # only enforced on Unified CI since it is specific to jobs after check-results job
    input.name == "CI"

    count(get_jobs_after_checkresults_without_ifalways(input.jobs)) > 0

    msg := sprintf("There are GitHub Actions jobs in Unified CI that depend on check-results but without specifying `if: always()...` which means they will get skipped! Affected job IDs: %s",
        [concat(", ", get_jobs_after_checkresults_without_ifalways(input.jobs))])
}

deny[msg] {
    # only enforced on Unified CI since it is specific to utils-flaky-tests-summary job
    input.name == "CI"
    input.jobs["utils-flaky-tests-summary"]

    jobs_with_flaky_outputs := get_jobs_with_flaky_outputs(input.jobs)
    flaky_summary_needs := {need | need := input.jobs["utils-flaky-tests-summary"].needs[_]}

    missing_jobs := jobs_with_flaky_outputs - flaky_summary_needs
    count(missing_jobs) > 0

    msg := sprintf("The utils-flaky-tests-summary job is missing dependencies on jobs with flakyTests outputs! Missing job IDs: %s",
        [concat(", ", missing_jobs)])
}

deny[msg] {
    # only enforced on Unified CI since it is specific to utils-flaky-tests-summary job
    input.name == "CI"
    input.jobs["utils-flaky-tests-summary"]

    needs_list := input.jobs["utils-flaky-tests-summary"].needs
    sorted_needs := sort(needs_list)

    needs_list != sorted_needs

    msg := sprintf("The utils-flaky-tests-summary job \"needs\" list is not alphabetically ordered! Expected: [%s], Got: [%s]",
        [concat(", ", sorted_needs), concat(", ", needs_list)])
}

warn[msg] {
    # only enforced on workflows that opted-in
    input.env.GHA_BEST_PRACTICES_LINTER == "enabled"

    count(get_jobs_without_printmetadata(input.jobs)) > 0

    msg := sprintf("There are GitHub Actions jobs without print-metadata step! Affected job IDs: %s",
        [concat(", ", get_jobs_without_printmetadata(input.jobs))])
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
        job_id != "get-concurrency-group-dynamically"
        job_id != "get-snapshot-docker-version-tag"
        job_id != "observe-aborted-jobs"
        job_id != "setup-tests"
        job_id != "utils-flaky-tests-summary"
        job_id != "fe-unit-tests-merge"

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
        not startswith(job_id, "utils-")

        # check if job declares dependency on "detect-changes" job anywhere
        job_needs_detectchanges := { need |
            need := job.needs[_]
            need == "detect-changes"
        }
        count(job_needs_detectchanges) == 0
    }
}

get_jobs_after_checkresults_without_ifalways(jobInput) = jobs_after_checkresults_without_ifalways {
    jobs_after_checkresults_without_ifalways := { job_id |
        job := jobInput[job_id]

        # check if job declares dependency on "check-results" job anywhere
        job_needs_checkresults := { need |
            need := job.needs[_]
            need == "check-results"
        }
        count(job_needs_checkresults) == 1

        # check that the job declares an `if: ...` condition and it contains `always()`
        #
        # this is important because GHA by default skips execution of jobs that itself
        # depend on jobs (transitively) that were skipped - which happens a lot in Unified CI
        # and we want to avoid accidentally skipping deploy jobs or similar
        #
        job_if := object.get(job, "if", "")  # get with empty default value
        not contains(job_if, "always() && needs.check-results.result == 'success'")
    }
}

get_jobs_with_flaky_outputs(jobInput) = jobs_with_flaky_outputs {
    jobs_with_flaky_outputs := { job_id |
        job := jobInput[job_id]

        # check if job has flakyTests output
        job.outputs.flakyTests
    }
}

get_jobs_without_printmetadata(jobInput) = jobs_without_printmetadata {
    jobs_without_printmetadata := { job_id |
        job := jobInput[job_id]

        # not enforced on Unified CI jobs that are part of change detection control flow structure
        job_id != "detect-changes"
        job_id != "setup-tests"
        job_id != "check-results"

        # not enforced on Unified CI jobs running after "check-results" job
        not startswith(job_id, "deploy-")
        not startswith(job_id, "utils-")

        # not enforced on jobs that invoke other reusable workflows (instead enforced there)
        not job.uses

        # check that there is at least one step printing metadata
        printmetadata_steps := { step |
            step := job.steps[_]
            step.name == "Print metadata"
            step.uses == "./.github/actions/print-metadata"
        }
        count(printmetadata_steps) == 0
    }
}
