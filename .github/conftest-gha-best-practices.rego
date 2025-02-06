package main

# This file contains rules checking GitHub Action workflow files for
# opinionated best practices in the C8 monorepo.

# The `input` variable is a data structure that contains a YAML file's contents
# as objects and arrays. See https://www.openpolicyagent.org/docs/latest/philosophy/#how-does-opa-work

deny[msg] {
    # only enforced on workflows that have jobs defined (prevents problems on empty YAML documents)
    input.jobs

    # check if the workflow specifies a human-readable name, helps with debugging
    not input.name

    msg := "This GitHub Actions workflow has no name property!"
}

deny[msg] {
    # The "on" key gets transformed by conftest into "true" due to some legacy
    # YAML standards, see https://stackoverflow.com/q/42283732/2148786 - so
    # "on.push" becomes "true.push" which is why below statements use "true"
    # instead of "on". Weird quirk...

    # This rule should encourage using "on.pull_request" trigger for workflows
    # that should run on PRs since that trigger allows to do change detection
    # and has more CI health metrics available (target branch).

    # enforce on workflows that have on.push trigger defined
    input["true"].push
    # but don't specify a set of branches
    not input["true"].push.branches

    msg := "This GitHub Actions workflows triggers on.push to any branch! Use on.pull_request for non-main/stable* branches."
}

deny[msg] {
    # This rule enforces the best practices listed and explained in
    # https://github.com/camunda/camunda/wiki/CI-&-Automation#caching-strategy

    count(get_jobs_with_setupnodecaching(input.jobs)) > 0

    msg := sprintf("There are GitHub Actions jobs using setup-node with caching instead of setup-yarn-cache! Affected job IDs: %s",
        [concat(", ", get_jobs_with_setupnodecaching(input.jobs))])
}

deny[msg] {
    # This rule prevents usage of forbidden "self-hosted" label in a "runs-on"
    # clause as described by Infra team: https://confluence.camunda.com/x/_IlZBw

    count(get_jobs_with_selfhostedlabel(input.jobs)) > 0

    msg := sprintf("There are GitHub Actions jobs using forbidden 'self-hosted' label in 'runs-on' clause! Affected job IDs: %s",
        [concat(", ", get_jobs_with_selfhostedlabel(input.jobs))])
}

warn[msg] {
    # This rule warns in situations where no "secrets: inherit" is passed on
    # calling other workflows as this is usually an oversight that prevents
    # even basic things like CI health metrics from working

    count(get_jobs_with_usesbutnosecrets(input.jobs)) > 0

    msg := sprintf("There are GitHub Actions jobs calling other workflows but not using 'secrets: inherit' which prevents access to secrets even for CI health metrics! Affected job IDs: %s",
        [concat(", ", get_jobs_with_usesbutnosecrets(input.jobs))])
}

###########################   RULE HELPERS   ##################################

get_jobs_with_setupnodecaching(jobInput) = jobs_with_setupnodecaching {
    jobs_with_setupnodecaching := { job_id |
        job := jobInput[job_id]

        setupnodecaching_steps := { step |
            step := job.steps[_]
            startswith(step.uses, "actions/setup-node")
            step["with"].cache == "yarn"
        }
        count(setupnodecaching_steps) > 0
    }
}

get_jobs_with_selfhostedlabel(jobInput) = jobs_with_selfhostedlabel1 | jobs_with_selfhostedlabel2 {
    # expression to check for jobs using "runs-on: self-hosted" notation (without array)
    jobs_with_selfhostedlabel1 := { job_id |
        job := jobInput[job_id]

        # not enforced on jobs that invoke other reusable workflows (instead enforced there)
        not job.uses

        job["runs-on"] == "self-hosted"
    }
    # expression to check for jobs using "runs-on: [self-hosted, ...]" notation (array)
    jobs_with_selfhostedlabel2 := { job_id |
        job := jobInput[job_id]

        # not enforced on jobs that invoke other reusable workflows (instead enforced there)
        not job.uses

        selfhosted_labels := { label |
            label := job["runs-on"][_]
            label == "self-hosted"
        }
        count(selfhosted_labels) > 0
    }
}

get_jobs_with_usesbutnosecrets(jobInput) = jobs_with_usesbutnosecrets {
    jobs_with_usesbutnosecrets := { job_id |
        job := jobInput[job_id]

        # check jobs that invoke other reusable workflows but don't specify "secrets: inherit"
        job.uses
        not job.secrets
    }
}
